# myPlanet refactor round — 10 work orders (performance quick wins)

Generated 2026-10-01. Plan-only deliverable: these are work orders for other coding agents
(jules, codex, copilot, devin, openhands, claude, qwen). Each task is independently
mergeable in any order; no file appears in more than one task.

## Open PRs checked (R3)

34 open PRs on `open-learning-exchange/myplanet` were listed and their file sets pulled
via `get_files` before scoping these tasks: #17812, #17811, #17776, #17694, #17680,
#17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808,
#15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848,
#13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075.

**Off-limits areas honored** (touched by open PRs, excluded from every task below):
`ui/teams/**`; `ui/voices/VoicesAdapter.kt`, `VoicesFragment.kt`, `VoicesViewModel.kt`,
`ReplyActivity.kt`, `VoicesActions.kt`; `ui/resources/ResourcesAdapter.kt`,
`ResourcesFragment.kt`, `ResourcesViewModel.kt`, `ResourceCardHelper.kt`;
`ui/courses/CoursesAdapter.kt`, `CoursesFragment.kt`, `CoursesViewModel.kt`,
`CourseDetailFragment.kt`, `CourseFilterController.kt`, `CourseSelectionController.kt`;
`ui/dashboard/DashboardViewModel.kt`, `BellDashboardFragment/ViewModel`,
`DashboardActivity`; `base/Base{Dashboard,Recycler,Resource,Voices}Fragment.kt`;
`model/{Course,Meetup,MyTeam,News,TeamTask,RealmMyCourse,RealmNews}.kt`; nearly all
`repository/*Impl.kt` in the teams/sync/upload chains; `services/sync/**`,
`services/upload/**`, `services/reminders/**`;
`utils/{FileUtils,TimeUtils,NotificationUtils,StreakUtils,LibraryTypeClassifier,MediumUtils,ActivityTracker,InAppNotificationHelper}.kt`;
`di/{RepositoryModule,RoomModule,ServiceModule}.kt`; `data/room/AppDatabase.kt` and the
DAO files those PRs touch; all `res/values*/strings.xml`; and
`app/build.gradle` / `settings.gradle` / `gradle/libs.versions.toml`.

Every path below was opened and confirmed before citing. Line numbers are approximate
anchors; executors must open the file before editing.

---

## Task 1 — ChatDetailFragment: de-quadratize the typing animation

**Roadmap:** 7 (performance hotspots). Also serves 10: the animation is pure
state-reduction — extracting it into a tested top-level helper keeps the future Compose
port free of view-embedded loop logic.

**Verified spot:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`,
`initChatComponents()` (~lines 270–281): a coroutine loops
`onUpdate(response.substring(0, currentIndex + 1))` with `delay(10L)` per character —
O(N²) string copying and N main-thread updates for an N-char AI answer.

**Work:**

1. Extract the loop into an internal top-level function in the same file, e.g.
   `internal fun typingAnimator(response: String, onUpdate: (String) -> Unit, onComplete: () -> Unit): suspend CoroutineScope.() -> Unit`
   — no new file needed if it stays small; a new file under `ui/chat/` is also
   acceptable (it is not PR-touched).
2. Advance by chunks (advance `currentIndex` by
   `minOf(4, response.length - currentIndex)` per tick, or to the next word boundary)
   keeping the 10 ms tick, the `isActive` bail-out, the final full-string emission, and
   the single `onComplete()` call.
3. Keep the returned `() -> Unit` cancel lambda contract of the `ChatAdapter` callback
   exactly as-is.

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`;
extend `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragmentTest.kt`
(exists, not PR-touched).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*ChatDetail*"`. Test: a
400-char response yields ≤ 400/4+1 updates, last update equals the full response,
`onComplete` fires once, cancellation mid-animation stops updates.

**Limits:** ≤120 changed lines, ≤2 files, no new dependencies, no TODOs.

## Task 2 — VoicesAdapterHelper: same quadratic fix, independent implementation

**Roadmap:** 7. Same north-star rationale as Task 1.

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt`,
`createOnAnimateTyping` (lines 13–31): identical per-character
`response.substring(0, currentIndex + 1)` + `delay(10L)` loop. (This file is NOT touched
by open PRs — #17776 touches `VoicesActions.kt`, #13415/#10993 touch
`VoicesAdapter.kt`/`VoicesFragment.kt` — none touch `VoicesAdapterHelper.kt`.)

**Work:**

1. Apply chunked/word-boundary advancement inside `createOnAnimateTyping` only. Do NOT
   share code with Task 1 (that would couple the two PRs) — duplicate the small logic
   inline.
2. Preserve the exact public signature
   `(String, (String) -> Unit, () -> Unit) -> (() -> Unit)`, the
   `dispatcherProvider.main` launch, the `isActive` check, and the cancel-lambda
   behavior.

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt`;
add `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelperTest.kt`
(new file — confirm still absent and not covered by an open PR at execution time; do NOT
touch `VoicesActionsTest.kt`, which belongs to PR #17776).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*VoicesAdapterHelper*"`.

**Limits:** ≤80 lines, ≤2 files, no new deps.

## Task 3 — CalendarFragment: precompute meetup-by-date map, hoist the formatter

**Roadmap:** 7; also 3/10 (moves per-tap computation into collector-side state — the
pattern a Compose port needs).

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt` — day-click
listener (lines 56–68) runs
`Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).toLocalDate()` per meetup
inside `filter` on every tap; `showAgendaDialog` (lines 71–86) creates a fresh
`DateTimeFormatter` per dialog.

**Work:**

1. In the existing `collectWhenStarted(viewModel.meetups)` block, build
   `meetupsByDate: Map<LocalDate, List<Meetup>>` via `groupBy` (converting each
   `startDate` once per emission, not once per tap).
2. Replace the tap-time `filter` with `meetupsByDate[clickedDate].orEmpty()`.
3. Hoist `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` into a `private val`
   (locale-dependent: capture `Locale.getDefault()` at fragment creation, as the current
   code effectively does).
4. Also fix the per-emission `Calendar.getInstance()` in the `calendarDays` mapping
   (lines 48–52): create one prototype `Calendar` per emission and `clone()` it per
   meetup before setting `timeInMillis` — each `CalendarDay` must own an independent
   `Calendar` (the applandeo library reads it later; sharing one instance is a bug).

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt`
only. Do NOT edit `ui/events/EventsAdapter.kt` (PR #15820) — only pass lists into its
existing constructor.

**Verify:** `./gradlew assembleDefaultDebug`;
`./gradlew testDefaultDebugUnitTest --tests "*Calendar*"`.

**Limits:** ≤100 lines, 1 file, no new deps.

## Task 4 — ServerReachabilityProvider: cache primary-server probes

**Roadmap:** 7, and 5 (sync consolidation: fewer redundant pre-sync probes).

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt` —
`isPrimaryServerReachable` (lines 47–50) always performs a live blocking `tryConnect`
(5 s timeout), while `isServerReachable` (lines 23–45) correctly uses the 30 s
`reachabilityCache`.

**Work:**

1. Give `isPrimaryServerReachable` the same cache-then-probe flow as
   `isServerReachable` (same `reachabilityCache`, same `REACHABILITY_CACHE_TTL_MS`,
   keyed by the raw URL string, but WITHOUT the `ServerUrlMapper` alternative-URL
   fallback — primary probing must stay exact).
2. Do not change `tryConnect`, `probe`, timeouts, or the TTL constant.

**Files:** `app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt`;
extend `app/src/test/java/org/ole/planet/myplanet/utils/ServerReachabilityProviderTest.kt`
(exists, not PR-touched).

**Verify:** add a test: two `isPrimaryServerReachable` calls within TTL hit the injected
`OkHttpClient` once (`coVerify(exactly = 1)` on `newCall`).
`./gradlew testDefaultDebugUnitTest --tests "*ServerReachability*"`.

**Limits:** ≤70 lines, ≤2 files, no new deps.

## Task 5 — FeedbackReplyAdapter: bounded date-format cache

**Roadmap:** 7; 3 (keeps the binder pure for a later Compose port).

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackReplyAdapter.kt`,
`onBindViewHolder` (lines 19–26): calls `TimeUtils.getFormattedDateWithTime(...)` on
every bind with no cache; sibling `FeedbackAdapter.kt` already proves the pattern
(`dateCache`, line 36).

**Work:**

1. Add a bounded `HashMap<Long, String>` date cache to `FeedbackReplyAdapter`, keyed by
   `feedbackReply.date.toLong()`, mirroring `FeedbackAdapter`'s approach but with
   eviction: clear when size exceeds 200 (simplest correct bound; no new class, no
   `android.util.LruCache` import needed).
2. Do NOT modify `FeedbackAdapter.kt`, `TimeUtils.kt` (off-limits via PR #15951), or
   strings resources.

**Files:**
`app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackReplyAdapter.kt` only
(not PR-touched). Tests: do not extend `FeedbackAdapterTest.kt` (different adapter). If
a reply-adapter test is wanted, add `FeedbackReplyAdapterTest.kt` (new; verify absence
at execution time), otherwise rely on the existing suite.

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*Feedback*"`.

**Limits:** ≤50 lines, ≤2 files, no new deps.

## Task 6 — LifeAdapter: replace reflection-based drawable lookup with compile-time map

**Roadmap:** 7 (hot bind path) + 8 (removes `getIdentifier` reflection, which lint
flags).

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`, `onBindViewHolder`
(lines 52–57): `context.resources.getIdentifier(imgId, "drawable", context.packageName)`
behind `drawableCache`. The key set is finite — the same file's `fragmentCache`
(lines 178–186) enumerates the seven ids, and all seven drawables exist:
`ic_mypersonals.xml`, `ic_submissions.png`, `ic_my_survey.xml`, `ic_myhealth.xml`,
`ic_calendar.xml`, `ic_references.png`, `my_achievement.png` (confirmed under
`app/src/main/res/drawable/`).

**Work:**

1. Add a companion `private val iconResByKey = mapOf("ic_mypersonals" to R.drawable.ic_mypersonals, "ic_submissions" to R.drawable.ic_submissions, "ic_my_survey" to R.drawable.ic_my_survey, "ic_myhealth" to R.drawable.ic_myhealth, "ic_calendar" to R.drawable.ic_calendar, "ic_references" to R.drawable.ic_references, "my_achievement" to R.drawable.my_achievement)`.
2. In `onBindViewHolder`, resolve `iconResByKey[imgId]`; on a miss keep today's
   `getIdentifier` fallback (via the existing `drawableCache`) so server-driven unknown
   ids degrade identically. A resId of `0` from the fallback must not be passed to
   `setImageResource` (today it silently clears — keep that behavior for misses by only
   calling `setImageResource` for non-zero ids, matching current effect).

**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`; extend
`app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt` (exists, not
PR-touched).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*Life*"`; add a case asserting
the seven known keys map to non-zero resource ids.

**Limits:** ≤70 lines, ≤2 files, no new deps.

## Task 7 — HealthExaminationAdapter: drop dead string concats, short-circuit the diff

**Roadmap:** 7 + 8.

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt` —
`checkEmpty`/`checkEmptyInt` (lines 126–132) do `value.toString() + ""` (dead
concatenation per call, ~7 calls per bind); `DIFF_CALLBACK` (lines 178–191) compares
`examination.data` (the large JSON blob) mid-chain instead of last.

**Work:**

1. Replace `value.toString() + ""` with `value.toString()` in both helpers.
2. Reorder `areContentsTheSame` so the cheap scalar fields run first and
   `oldItem.examination.data == newItem.examination.data` is the final `&&` clause
   (short-circuit avoids deep string compares on the common unchanged path). Do not drop
   any compared field.

**Files:**
`app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt`;
extend `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapterTest.kt`
(exists, not PR-touched).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*HealthExamination*"`.

**Limits:** ≤40 lines, ≤2 files, no new deps.

## Task 8 — PersonalsAdapter + ReferencesAdapter: hoist click listeners out of bind

**Roadmap:** 7.

**Verified spots:**
`app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt`
`onBindViewHolder` (lines 34–64) allocates four listener lambdas per bind;
`app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt`
`onBindViewHolder` (lines 25–37) allocates one listener per bind and branches on the raw
`bindingAdapterPosition == 0` to pick the target activity.

**Work:**

1. `PersonalsAdapter`: move all four `setOnClickListener` calls into
   `PersonalsViewHolder.init` (the existing lambdas already use
   `holder.bindingAdapterPosition` — relocate unchanged); `onBindViewHolder` then sets
   only `title`, `description`, and the cached date.
2. `ReferencesAdapter`: move the listener into `ViewHolderReference`'s `init` and
   resolve the destination from `getItem(bindingAdapterPosition)` by item index relative
   to the current list (keep exact current behavior: position 0 → `OfflineMapsActivity`,
   else → `DictionaryActivity`; implement as `if (holder.bindingAdapterPosition == 0)`
   inside the hoisted listener — the fix is allocation hoisting, not navigation
   semantics).
3. Guard every listener body with `RecyclerView.NO_POSITION` checks (PersonalsAdapter
   already does; add it to ReferencesAdapter).

**Files:** those two adapter files only. Neither is PR-touched; there is no
`ui/references` test dir — do not create one (rely on `PersonalsAdapterTest.kt`, which
exists and is not PR-touched, for the Personals change).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*Personals*"`;
`./gradlew assembleDefaultDebug`.

**Limits:** ≤110 lines, 2 files, no new deps.

## Task 9 — ActivitiesFragment: single rebuild-only chart configuration

**Roadmap:** 7.

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt` —
`renderChart` (lines 33–80) reconfigures the entire MPAndroidChart axis/legend/formatter
stack and re-allocates the anonymous `ValueFormatter` on every `monthlyLoginCounts`
emission; `private val months = DateFormatSymbols().months` (line 21) is fine but the
formatter closure re-captures it every emission.

**Work:**

1. Split `renderChart` into `configureChartOnce(textColor: Int)` (axis, legend,
   granularity, the `ValueFormatter` — installed once from `onViewCreated`) and
   `updateChartData(monthlyCounts)` (entries/`BarDataSet`/`BarData`/`invalidate()` +
   visibility toggles).
2. Call `configureChartOnce` once in `onViewCreated`; emissions call only
   `updateChartData`.
3. Keep the empty-state visibility logic and all colors/text sizes identical.

**Files:**
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt`; extend
`app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragmentTest.kt`
(exists, not PR-touched). Do NOT touch `BellDashboardFragment.kt`,
`BellDashboardViewModel.kt`, `DashboardViewModel.kt`, or `DashboardActivity.kt` (all
PR-touched).

**Verify:** `./gradlew testDefaultDebugUnitTest --tests "*ActivitiesFragment*"`.

**Limits:** ≤80 lines, ≤2 files, no new deps.

## Task 10 — InlineResourceAdapter: skip redundant status resets on unchanged binds

**Roadmap:** 7.

**Verified spot:**
`app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` —
`updateStatusAndPreview` (lines 155–204) unconditionally flips five view visibilities
and relaunches a preview coroutine (`holder.setPreviewJob(adapterScope.launch { ... })`)
on every bind, even when payloads only changed the title; the payload path
(lines 113–140) already routes `PAYLOAD_TITLE` separately, but `PAYLOAD_ADDRESS` and
`PAYLOAD_STATUS` both re-run the full reset+launch, and `onBindViewHolder(position)`
(lines 142–153) re-runs everything on plain rebinds of unchanged items (DiffUtil won't
call it for equal items, but recyclers rebind on scroll-backed recycling).

**Work:**

1. In `updateStatusAndPreview`, short-circuit when the view holder is already showing
   the correct terminal state for this resource: track the last bound `resource.id` +
   `resource.isResourceOffline()` + `resource.resourceLocalAddress` triple on the
   `ViewHolder` (add three `internal var`s, or a small `internal data class BoundKey`);
   if the triple matches and `pbDownload.visibility` was already resolved, skip the
   visibility flips and the coroutine relaunch.
2. Clear the triple in `cancelPreviousPreviews()` (recycle path) so recycled holders
   always re-bind.
3. Keep all preview-type branches (`showImagePreview`, `showVideoPreview`,
   `showPdfPreview`, `showHtmlPreview`, `showAudioPreview`, `showCsvPreview`,
   `showTextPreview`) and the `htmlCoverCache` logic byte-for-byte identical.

**Files:**
`app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` only
(not PR-touched — PR #13657 touches
`CoursesFragment/CoursesViewModel/CourseFilterController/CourseSelectionController`, and
PRs touch `CoursesAdapter.kt`; none touch this file). If a test file for this adapter
exists at execution time, extend it; do not create one otherwise.

**Verify:** `./gradlew assembleDefaultDebug`;
`./gradlew testDefaultDebugUnitTest --tests "*InlineResource*"` (skip silently if no
such test exists).

**Limits:** ≤90 lines, 1–2 files, no new deps.

---

## Self-check (P5)

- **R1**: 10 tasks, each a standalone diff against `master`, mergeable in any order. ✓
- **R2**: file × task uniqueness — Task 1 `ChatDetailFragment.kt` (+ its test); Task 2
  `VoicesAdapterHelper.kt` (+ new helper test); Task 3 `CalendarFragment.kt`; Task 4
  `ServerReachabilityProvider.kt` (+ its test); Task 5 `FeedbackReplyAdapter.kt`;
  Task 6 `LifeAdapter.kt` (+ `LifeAdapterTest.kt`); Task 7
  `HealthExaminationAdapter.kt` (+ its test); Task 8 `PersonalsAdapter.kt`,
  `ReferencesAdapter.kt`; Task 9 `ActivitiesFragment.kt` (+ its test); Task 10
  `InlineResourceAdapter.kt`. No file repeats. ✓
- **R3**: open PRs listed above; every cited file cross-checked against the 34 PRs'
  file lists. ✓
- **R4**: every path, class, function, drawable name, and line anchor above was
  opened/grepped before citing. ✓
- **R5**: each task ≤ ~120 changed lines, ≤ 2–3 files, zero new dependencies, zero TODO
  placeholders, zero dead code. ✓
- **R6**: plan only — no implementation code included. ✓
