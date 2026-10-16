# myPlanet refactor mega plan — 2026-10-16

One backlog merged from the three merged plans, each re-checked against `master` @ `897cdbd`:

| Key | Plan | Tasks | Strength taken from it |
|---|---|---:|---|
| **C** | Claude — [`claude/pensive-wozniak-35584a`](https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-wozniak-35584a/docs/refactor_tasks.md) | 88 | Per-task *Verified / Do / Acceptance*, line numbers, sequencing notes |
| **X** | Codex — [`codex/verified-refactor-plan-2026-10-16`](https://github.com/open-learning-exchange/myplanet/blob/codex/verified-refactor-plan-2026-10-16/docs/refactor-review-2026-10-16/refactor_tasks.md) | 82 | Rejections with reasons, behaviour-preservation constraints |
| **D** | Devin — [`devin/1792143542-refactor-task-merge`](https://github.com/open-learning-exchange/myplanet/blob/devin/1792143542-refactor-task-merge/docs/refactor_tasks.md) | 98 | Open-PR ownership flags |

Result: **66 tasks in five waves** plus **22 rejected proposals** with reasons. Where the plans disagreed, the code on master decided; each such call is noted under *Adjudication*. Four tasks are new or reworked here (B3, B2's SQL, C20, D4), because no source recipe was both safe and worthwhile.

## How to use this plan

- **One task = one PR** unless the task says otherwise. Work wave by wave; inside a wave, go top-down (sorted by rating).
- **PR title:** house style `scope: smoother thing doing (fixes #N)` (see the `merge-prepping` skill); open a tracking issue per task so it closes on merge.
- **Before starting:** re-check the shared-file and open-PR table at the bottom; re-verify line numbers if `master` has moved past `897cdbd`.
- **Test tasks (wave C):** put the class's `<testsuite time>` before and after in the PR body. Doc-only PRs run only the labels check.
- **Never** delete a test that asserts behaviour to save time; never skip or quarantine one.

**Rating** = 100 × E^0.35 × I^0.40 × F^0.25, with E = evidence (verified on master), I = impact (correctness > user latency > CI time > hygiene), F = risk-adjusted feasibility, each 0–100. The *Sources* line shows each plan's own id and rating; `—` = not proposed, `dropped` = proposed upstream but rejected by that plan.

Paths are relative to `app/src/main/java/org/ole/planet/myplanet/` and `app/src/test/java/org/ole/planet/myplanet/` (`test:` prefix) unless they start with `.github/` or `docs/`.

## Start here: the first ten PRs

| # | Task | Rating | Why first |
|---|---|---:|---|
| 1 | **A1** MyLifeDao.updateVisibility: resolve to exactly one row | 87 | Cross-user data corruption |
| 2 | **B1** CoursesStepsViewModel: publish step content before the server probe | 83 | Offline course steps render blank for 5–15 s |
| 3 | **C1** ServerUrlMapperReachabilityTest: make the closed-port case fail fast | 76 | CI wall clock, no coverage loss |
| 4 | **A2** ImageUtils: clear the pending Glide request before an empty-source placeholder | 74 | Visible bug, small diff |
| 5 | **A3** Health patient picker: one owned load, no sort race, adapter set once | 72 | Visible bug, small diff |
| 6 | **A4** ChatHistoryAdapter: each card announces its own title | 70 | Visible bug, small diff |
| 7 | **C2** Retire the one-off Robolectric SDK 27/28 sandboxes | 69 | CI wall clock, no coverage loss |
| 8 | **B2** TeamDao.observeAll: read only membership and root-team rows | 68 | User-facing latency |
| 9 | **A5** TeamDao: chunk getByIds/deleteByIds inside the DAO | 67 | Visible bug, small diff |
| 10 | **B3** RetryInterceptor: stop backing off when the device has no network | 67 | User-facing latency |

## Overview

| Wave | Theme | Tasks | Rating range |
|---|---|---:|---|
| A | Correctness: user-visible bugs | 10 | 41–87 |
| B | User-facing latency and jank | 13 | 47–83 |
| C | Test-suite speed and health | 22 | 36–76 |
| D | Architecture boundaries and hygiene | 13 | 25–50 |
| E | Optional micro-optimisations | 8 | 21–37 |

---

## Wave A — Correctness: user-visible bugs

Wrong rows written, wrong data shown, wrong accessibility labels. Highest value per line changed.

### A1 · MyLifeDao.updateVisibility: resolve to exactly one row

**Rating 87** (E 95 · I 85 · F 80) · Sources: C R1 (64) · X R001 (96) · D #12 (82)

**Files:** `data/room/dao/MyLifeDao.kt`, `test: data/room/dao/MyLifeDaoTest.kt`

**Verified on master:** `UPDATE my_life SET isVisible = :isVisible WHERE _id = :id OR imageId = :id OR title = :id` (`MyLifeDao.kt:33`) has no owner constraint. `LifeFragment.kt:52-54` falls back to `imageId`/`title` when `_id` is blank, so a shared default tile label flips every user's matching row.

**Do:** Wrap in `@Transaction`: update by exact `_id` if it exists; otherwise update only when `imageId`/`title` matches **exactly one** row, and update that row by its resolved key. Ambiguous or unknown id is a no-op. Keep the two-argument signature.

**Acceptance:** DAO tests: cross-user alias collision (no other user's row changes), exact `_id` hit, unique alias hit, unknown id.

**Adjudication:** Devin resolved the row in `LifeFragment`; that still matches other users' rows sharing the alias. Fix it in the DAO (Claude + Codex).

### A2 · ImageUtils: clear the pending Glide request before an empty-source placeholder

**Rating 74** (E 90 · I 55 · F 90) · Sources: C P7 (64) · X R004 (93) · D #11 (82)

**Files:** `utils/ImageUtils.kt`, `test: utils/ImageUtilsTest.kt`

**Verified on master:** `loadProfileImage` (:10-12) and `loadImage` (:38-39) call `setImageResource` for null/empty sources without `Glide.with(view).clear(view)`; a recycled row's in-flight request then overwrites the placeholder.

**Do:** Clear first in both empty branches. Change nothing on the non-empty path.

**Acceptance:** Test verifies `clear(view)` precedes the placeholder; no stale portraits while scrolling members/chat lists.

### A3 · Health patient picker: one owned load, no sort race, adapter set once

**Rating 72** (E 90 · I 55 · F 80) · Sources: C P4 (63) · X R005 (91) + R012 (85) · D #38 (78) + #48 (75)

**Files:** `ui/health/MyHealthFragment.kt`, `ui/health/HealthViewModel.kt`, `test: ui/health/HealthViewModelTest.kt`

**Verified on master:** `loadPatients()` (`MyHealthFragment.kt:272`, `descending=true`) launches untracked (`HealthViewModel.kt:95-99`); the spinner's first `onItemSelected` then calls `searchPatients(…, "joinDate", false)` (:304), so two loads race in opposite orders. Lines 196-198 reassign layoutManager and adapter on every emission.

**Do:** One PR: drop the fragment's `loadPatients()` call (the spinner's first selection is the initial load) and route any remaining list load through `searchJob` so a newer request cancels an older one; reset loading state in `finally`. Assign layoutManager/adapter only when `rvRecords.adapter !== healthAdapter`.

**Acceptance:** List appears once, in the spinner's order; a stale load can't overwrite a newer query (test with a suspended first load); records strip keeps its scroll position.

**Adjudication:** Codex split this into two tasks touching the same two files; merged to one PR.

### A4 · ChatHistoryAdapter: each card announces its own title

**Rating 70** (E 95 · I 45 · F 95) · Sources: C P19 (44) · X R009 (86) · D #37 (78)

**Files:** `ui/chat/ChatHistoryAdapter.kt`

**Verified on master:** Adapter-wide `private var chatTitle` (:54) is written in `onBindViewHolder` (:137, :141) and read for `chatCardView.contentDescription` (:101), so a card announces whichever row was bound last.

**Do:** Delete the field; derive the description from the bound item (first query, else `item.title`). Leave the share-dialog plumbing alone.

**Acceptance:** Binding row 2 then reading row 1's card description returns row 1's title (Robolectric adapter test).

**Adjudication:** This is an accessibility bug, not a performance one. Devin says the field is read in `onCreateViewHolder`; it is not.

### A5 · TeamDao: chunk getByIds/deleteByIds inside the DAO

**Rating 67** (E 90 · I 45 · F 85) · Sources: C R2 (60) · X R007 (86) · D #45 (76)

**Files:** `data/room/dao/TeamDao.kt`, `test: data/room/dao/TeamDaoTest.kt`

**Verified on master:** Single unbounded `IN (:ids)` at :13 and :35. Readers chunk at call sites, but `deleteLocalTeamRecords` (`TeamsRepositoryImpl.kt:130-136`) passes every id through. minSdk 26 devices have SQLite's 999-variable limit.

**Do:** Internal queries + default wrappers: `distinct().chunked(900)`; flatten reads, sum delete counts, delete under `@Transaction` — same shape as the other chunked DAOs.

**Acceptance:** Room test with 1201+ ids (duplicates, unknowns): exact counts, unrelated row survives.

**Adjudication:** Same file as B2; land this first (smaller diff).

### A6 · TakeCourseViewModel: log one visit per course open, not per onStart

**Rating 64** (E 85 · I 45 · F 75) · Sources: C P6 (60) · X R033 (81) · D #13 (82)

**Files:** `ui/courses/TakeCourseViewModel.kt`, `test: ui/courses/TakeCourseViewModelTest.kt`

**Verified on master:** `collectLatestWhenStarted(uiState)` (`TakeCourseFragment.kt:74`) → `bindCourse` → `setCourseData()` → `logCourseVisit` (:241) inserts a `visit` row on every resume (`ActivitiesRepositoryImpl.kt:97-110`), inflating Planet's activity reports.

**Do:** Track `loggedVisitKey = courseId+userId` in the ViewModel; return early on repeat; set it only after a successful insert (a failure retries next time); guard concurrent calls with the same key.

**Acceptance:** Three resumes → one row; reopen the course (new ViewModel) → one more; different course → one more.

**Adjudication:** **Confirm with Planet maintainers that a 'visit' means an open, not a resume** before merging.

### A7 · SubmissionsRepositoryExporter: pair each submission with its own exam's questions

**Rating 60** (E 75 · I 40 · F 85) · Sources: C R8 (36) · X R002 (95) · D #9 (84)

**Files:** `repository/SubmissionsRepositoryExporter.kt`, `test: repository/SubmissionsRepositoryExporterTest.kt`

**Verified on master:** Questions are loaded once from `submissions.firstOrNull()?.parentId` (:184-185). **Latent today**: the only caller (`SubmissionListFragment.generateReport`) exports one `parentId`, so mixed-exam exports aren't reachable yet.

**Do:** Group questions by exam id with one batched query; pick the matching list inside the render loop. Preserve ordering, parent-id parsing, labels, pagination.

**Acceptance:** Helper test: mixed exams, repeated exam, missing parent.

**Adjudication:** Codex rated this 95 without noting the single caller; Claude's 'latent' caveat holds. Cheap and correct, so keep it in A, just not first.

### A8 · ResourcesPreviewLoader: stop CSV parsing at the fifth record

**Rating 58** (E 90 · I 30 · F 90) · Sources: C P17 (44) · X R032 (82) · D #62 (72)

**Files:** `utils/ResourcesPreviewLoader.kt`, `test: utils/ResourcesPreviewLoaderTest.kt`

**Verified on master:** `for (row in reader) { if (count >= 5) break … }` (:66-70) pulls a sixth record before breaking, so a malformed sixth record throws and nulls the whole preview.

**Do:** Check the limit before each `readNext()`. Keep formatting, multiline-CSV semantics and resource closing.

**Acceptance:** Five valid rows followed by an unterminated quote still preview.

### A9 · DashboardSurveysAdapter: guard NO_POSITION; set text colour once

**Rating 48** (E 85 · I 20 · F 90) · Sources: C P20 (37) · X R051 (73) · D #83 (part) (67)

**Files:** `ui/dashboard/DashboardSurveysAdapter.kt`

**Verified on master:** Click listener passes raw `bindingAdapterPosition` to `onItemClick` (:30) — `-1` during removal animations — and `setTextColor` runs per bind (:44) although `textColor` is a holder `val`.

**Do:** Return early on `RecyclerView.NO_POSITION` before the callback **and** before `dialog.dismiss()`; set the colour in holder `init`. Leave `ReferencesAdapter` alone.

**Acceptance:** A click during removal never reaches `onItemClick(-1)`.

**Adjudication:** Devin calls the position claim 'stale' because `bindingAdapterPosition` is used; the missing guard is the bug.

### A10 · UserDao: chunk getUsersByAnyIds/getGuestUsersByNames inside the DAO

**Rating 41** (E 80 · I 15 · F 80) · Sources: C R9 (31) · X R038 (80) · D #59 (72)

**Files:** `data/room/dao/UserDao.kt`, `test: data/room/dao/UserDaoTest.kt`

**Verified on master:** Unbounded `IN` at :13-14 and :45-46, but every current caller already chunks (400/400/500 at `UserRepositoryImpl.kt:115, 1099, 1104`). Defensive only.

**Do:** Dedup + `chunked(450)` for the double-`IN` any-id query, `chunked(900)` for names; dedup combined users by local id.

**Acceptance:** Room tests with 1201+ inputs.

---

## Wave B — User-facing latency and jank

Things a learner waits on: opening a course step, offline sync failure, chat resume, typing in exams.

### B1 · CoursesStepsViewModel: publish step content before the server probe

**Rating 83** (E 95 · I 75 · F 80) · Sources: C P2 (75) · X R003 (93) · D #5 (86)

**Files:** `ui/courses/CoursesStepsViewModel.kt`, `test: ui/courses/CoursesStepsViewModelTest.kt`

**Verified on master:** `loadStep` awaits `configurationsRepository.checkServerAvailability()` (:84) before assigning `_uiState` (:106); offline, a step with undownloaded resources stays blank for the whole probe.

**Do:** Assign title/markdown/resources/exam state first with `isDownloadingResources=false`; probe inside the same step-load job and `update { if (it.stepId == thisStep) copy(isDownloadingResources = true) }`, so switching steps cancels the probe and stale results are dropped.

**Acceptance:** With a suspended probe the test sees `step` set and `isLoading=false`; switching steps mid-probe never flips the new step's flag; the existing download-flag test passes.

### B2 · TeamDao.observeAll: read only membership and root-team rows

**Rating 68** (E 85 · I 55 · F 70) · Sources: C P5 (65) · X R010 (85) · D #19 (80)

**Files:** `data/room/dao/TeamDao.kt`, `test: data/room/dao/TeamDaoTest.kt`, (stretch, separate PR) repository/TeamsRepositoryImpl.kt

**Verified on master:** `SELECT * FROM teams` (:19) feeds `getMyTeamsFlow` (:209) and `getMyTeamDetailsFlow` (:295), which keep only `docType == "membership"` rows and `isRootTeam()` (= `teamId.isNullOrBlank()`, :1400) rows. Every report/transaction/link/request row is decoded and thrown away on each `teams` write.

**Do:** Add `observeMembershipAndRootRows()`: `WHERE docType = 'membership' OR teamId IS NULL OR teamId NOT GLOB '*[0-9A-Za-z]*'`. Any blank string (ASCII or Unicode whitespace) contains no alphanumerics, so the SQL is a **strict superset** of the Kotlin predicate, while real child rows (CouchDB ids are alphanumeric) are excluded. Keep every existing Kotlin filter unchanged as the final word. Stretch PR: user-scoped membership query + batched `markMembershipsForLeave` (:1367).

**Acceptance:** DAO test: report/link rows excluded; membership rows, `NULL`, `''`, `' '` and form-feed-only `teamId` rows all included. Dashboard teams, teams list and calendar unchanged.

**Adjudication:** Claude's `TRIM(teamId, ' '||char(9,10,13))=''` misses form feed and Unicode whitespace (Codex's catch); Codex then dropped the cheap DAO fix entirely. The `GLOB` form keeps the cheap fix and the guarantee.

### B3 · RetryInterceptor: stop backing off when the device has no network

**Rating 67** (E 80 · I 55 · F 70) · Sources: C P1 (79) · X dropped · D #2 (90)

**Files:** `data/api/RetryInterceptor.kt`, `di/NetworkModule.kt`, `test: data/api/RetryInterceptorTest.kt`

**Verified on master:** Every `IOException` is retried (`catch (e: IOException)`, :50-53) with `maxRetries=3`, `initialDelay=1000`, `factor=2.0` (:18-20): roughly 7 s before an offline request fails. Non-5xx responses (including 4xx) are already returned immediately.

**Do:** Inject a connectivity check (a `() -> Boolean` backed by `NetworkUtils.isNetworkConnectedFlow.value`, stubbable in tests). Before each backoff, if there is no active network, stop retrying and rethrow/return the last result. **Keep** retrying `UnknownHostException`/`SSLHandshakeException` while a network exists.

**Acceptance:** Offline: `proceed` called once, no backoff, no broadcast. Online + transient DNS failure: retries as today.

**Adjudication:** **Reworked.** Claude's version classified DNS/TLS failures as permanent; Codex rightly rejected that for a flaky-network app (Wi-Fi waking, captive portals). Devin's premise (4xx retried) is false. This version keeps the 7 s win where it matters — no network at all — without dropping recoverable retries.

### B4 · Chat history: stop full reloads on every onStart; run the two reads in parallel

**Rating 67** (E 85 · I 50 · F 75) · Sources: C P3 (65) · X R006 (86) · D #8 (84)

**Files:** `ui/chat/ChatHistoryFragment.kt`, `ui/chat/ChatViewModel.kt`, `test: ui/chat/ChatViewModelTest.kt`

**Verified on master:** `_refreshChatSignal = MutableSharedFlow(replay = 1)` (:70) is collected with `collectWhenStarted` (`ChatHistoryFragment.kt:206`, `repeatOnLifecycle(STARTED)`), so the replayed `Unit` reruns `loadChatHistoryScreenData` on every foreground. Planet news and history load sequentially (:139-140). Nothing ever emits `"chats"` on the realtime bus (only `"courses"` is emitted, `CoursesRepositoryImpl.kt:253/370/431`), so the init seed is the only real signal.

**Do:** Collect the signal once per view (`viewLifecycleOwner.lifecycleScope.launch`, no `repeatOnLifecycle`); `async` the two independent reads; build shared-view ids on `dispatcherProvider.default`. Keep `replay = 1` (a test relies on it).

**Acceptance:** Home → back shows no reload; sharing still refreshes; view destruction still cancels.

**Adjudication:** Devin's recipe (cache 'remote revision' timestamps) describes state that doesn't exist; ignore it.

### B5 · VoicesAdapterHelper: reuse TextRevealer's chunked reveal

**Rating 67** (E 90 · I 45 · F 85) · Sources: C P8 (65) · X R015 (85) · D #36 (78)

**Files:** `ui/voices/VoicesAdapterHelper.kt`, `test: utils/TextRevealerTest.kt`

**Verified on master:** `createOnAnimateTyping` emits one substring per character with `delay(10)` (:19-26). `TextRevealer.reveal` already chunks up to 8 characters per tick and is used by chat (`ChatDetailFragment.kt:272`).

**Do:** Delegate to `TextRevealer.reveal`, keeping the supplied scope/dispatcher, cancellation closure and signature; complete exactly once, never after cancellation.

**Acceptance:** A 3000-character reply gives ≤ 375 updates; empty string completes once; cancellation stops callbacks.

### B6 · BaseExamFragment: run Markwon editor highlighting off the main thread

**Rating 63** (E 85 · I 45 · F 70) · Sources: C P9 (60) · X R019 (83) · D #24 (80)

**Files:** `base/BaseExamFragment.kt`, `test: base/BaseExamFragmentTest.kt`

**Verified on master:** `MarkwonEditorTextWatcher.withProcess(markwonEditor)` (:191) re-parses on every keystroke on the main thread. `markwon-editor` 4.6.2 (already a dependency) offers `withPreRender`.

**Do:** Lazy single-thread `ExecutorService`; `withPreRender(editor, executor, etAnswer)`; remove the old watcher when re-binding; `shutdownNow()` in `onDestroyView` if initialised; never write to a destroyed view.

**Acceptance:** Typing 500+ characters of markdown stays smooth; no leaked threads after leaving the exam.

**Adjudication:** Devin passes `Dispatchers.IO` — the API takes an `ExecutorService`.

### B7 · ServerReachabilityProvider: probe primary and alternative URLs concurrently

**Rating 62** (E 85 · I 50 · F 55) · Sources: C P10 (57) · X R030 (82) · D #14 (82)

**Files:** `utils/ServerReachabilityProvider.kt`, `test: utils/ServerReachabilityProviderTest.kt`

**Verified on master:** `isServerReachable` tries `urlsToTry` sequentially (:37-42); it gates SyncActivity, chat, the bell dashboard and 10+ other call sites.

**Do:** Race distinct candidates; first `true` wins; cancel the loser's blocking OkHttp `Call` via `invokeOnCompletion`, close responses. Keep the TTL cache and HEAD→GET fallback.

**Acceptance:** Alternative succeeds while primary blocks; the primary's `cancel()` is invoked; cache behaviour unchanged.

**Adjudication:** Medium risk — the cancellation plumbing around blocking `execute()` is the hard part. Land before D1 and D3, which build on this provider.

### B8 · KeyboardUtils.setupUI: one shared listener, hide only on ACTION_DOWN

**Rating 61** (E 90 · I 35 · F 85) · Sources: C P11 (56) · X R013 (85) · D #29 (78)

**Files:** `utils/KeyboardUtils.kt`, `test: utils/KeyboardUtilsTest.kt`

**Verified on master:** `setupUI` creates a new `OnTouchListener` per view that calls `hideSoftKeyboard` on every `MotionEvent`, including each `ACTION_MOVE` (:20-37). Seven call sites.

**Do:** One listener per `setupUI` call shared across the tree; act on `actionMasked == ACTION_DOWN`; **return false** so gestures propagate; recursion in a private helper; keep EditText exclusions.

**Acceptance:** MOVE doesn't hide; all children share one instance; scrolling still works.

**Adjudication:** Same file as D9 (printStackTrace) — sequence.

### B9 · InlineResourceAdapter: coalesce payloads into one preview refresh

**Rating 56** (E 85 · I 30 · F 85) · Sources: C P12 (52) · X R014 (85) · D #20 (80)

**Files:** `ui/courses/InlineResourceAdapter.kt`, `test: ui/courses/InlineResourceAdapterTest.kt`

**Verified on master:** The payload bind iterates payloads (:122); both `PAYLOAD_ADDRESS` (:127-129) and `PAYLOAD_STATUS` (:131) call `updateStatusAndPreview`, and the diff emits both when a download completes (:48-49).

**Do:** Flatten payloads into a flag set; refresh title once and status/preview once; keep the full-bind fallback and previous-job cancellation.

**Acceptance:** A combined address+status payload triggers exactly one preview refresh.

**Adjudication:** Devin puts the file in `ui/resources` and merges in a `File.exists()` cache; the cache is rejected (see Rejected).

### B10 · ResourcesListFilter: prepare selected mediums once and short-circuit

**Rating 56** (E 85 · I 30 · F 85) · Sources: C P15 (53) · X R018 (84) · D #21 (80)

**Files:** `ui/resources/ResourcesListFilter.kt`, `test: ui/resources/ResourcesListFilterTest.kt`

**Verified on master:** `matchesFacets` evaluates subject, level, language and medium eagerly (:71-84); `matchesMedium` canonicalises each *selected* medium per library row (:90-91) and classifies even when no target type needs it.

**Do:** Precompute canonical selected mediums/target types once per `matching()` call; return early in facet order; resolve the row's canonical medium at most once. **Keep `equals(ignoreCase = true)`** for subject/level/language.

**Acceptance:** `apply` and `countMatching` agree; same results across mixed-case and alias cases.

**Adjudication:** Devin/Copilot wanted `lowercase()` equality — that allocates and can change Unicode results.

### B11 · Opened-resources flow: one row per resource; skip the discarded cached sort

**Rating 55** (E 85 · I 30 · F 80) · Sources: C P13 (52) · X R016 (84) · D #39 (78)

**Files:** `data/room/dao/ResourceActivityDao.kt`, `ui/resources/ResourcesViewModel.kt`, `test: data/room/dao/ResourceActivityDaoTest.kt`

**Verified on master:** `observeByUserAndType` (:29-30) returns every open event; its only caller reduces it to `mapNotNull { resourceId }.toSet()` (`ResourcesRepositoryImpl.kt:551-555`). `loadResources` runs `getCachedResources` even when `_resourcesState` is non-empty and discards the result (:98-101).

**Do:** Add `AND resourceId IS NOT NULL GROUP BY resourceId`; guard the cached sort behind the `isEmpty` check.

**Acceptance:** DAO test: `{A×3, B, null, other-user}` → 2 rows; opened indicator unchanged.

**Adjudication:** Same ViewModel as E6 — sequence.

### B12 · RequestsAdapter + MembersAdapter: diff row snapshots instead of a full-range rebind

**Rating 48** (E 80 · I 25 · F 65) · Sources: C — · X R048 (74) + R057 (71) · D #54 (74) + #83 (part)

**Files:** `ui/teams/members/RequestsAdapter.kt`, `ui/teams/members/MembersAdapter.kt`

**Verified on master:** `RequestsAdapter.setData` calls `submitList(members) { if (capacityChanged) notifyItemRangeChanged(0, itemCount) }` (:36-43); `MembersAdapter` does `submitList(newList) { notifyItemRangeChanged(0, itemCount, PAYLOAD_KEY_…) }` (:187). Every row rebinds after the diff.

**Do:** Submit immutable row snapshots carrying the user plus the capacity/leader/limit values each row renders; let `DiffUtil` produce payloads; drop the post-diff range notify. Keep `setData`/`updateData` signatures and accept/reject/guest rules. Two PRs, one per adapter.

**Acceptance:** Changing leadership rebinds only affected rows; accept/reject still enables/disables correctly at the member limit.

**Adjudication:** Not in Claude's plan (silently omitted).

### B13 · FreeSpaceWorker: throttle setProgress during the delete walk

**Rating 47** (E 80 · I 20 · F 85) · Sources: C P18 (44) · X R043 (76) · D #66 (72)

**Files:** `services/FreeSpaceWorker.kt`, `test: services/FreeSpaceWorkerTest.kt`

**Verified on master:** `setProgress` runs every 10 deleted files (:76-77); each is a WorkManager progress write.

**Do:** Throttle by time (e.g. ≥ 500 ms) or count, and always send a final update. Preserve cancellation, cv-directory exclusion and accounting.

**Acceptance:** Far fewer updates; final update fires; same files deleted.

---

## Wave C — Test-suite speed and health

Shorter CI wall clock without losing coverage. Every task records the class `<testsuite time>` before and after.

### C1 · ServerUrlMapperReachabilityTest: make the closed-port case fail fast

**Rating 76** (E 95 · I 55 · F 95) · Sources: C T1 (74) · X R044 (76) · D #26 (80)

**Files:** `test: services/sync/ServerUrlMapperReachabilityTest.kt`

**Verified on master:** The test binds `HttpServer` on port 0, stops it, then probes; `isUrlDirectlyReachable` uses 5000 ms timeouts (`ServerUrlMapper.kt:122-123`). Reproduced by two plans independently: **~5025 ms** for the just-stopped port vs **2 ms** for `http://127.0.0.1:1`.

**Do:** Drop the dummy server in that one case; probe `http://127.0.0.1:1`; keep `assertFalse`. Don't touch production timeouts or the four local-server cases.

**Acceptance:** All five cases pass; the closed-port case < 1 s.

**Adjudication:** Devin suggests injecting a shorter timeout into production code — unnecessary.

### C2 · Retire the one-off Robolectric SDK 27/28 sandboxes

**Rating 69** (E 85 · I 55 · F 75) · Sources: C T2 (68) · X R021 (83) · D #3 (88)

**Files:** `test: utils/SecurePrefsTest.kt`, `test: utils/PdfThumbnailLoaderTest.kt`, `test: utils/VersionUtilsTest.kt (method pin at :179 only)`

**Verified on master:** API 27 is pinned only by `SecurePrefsTest.kt:20`; API 28 only by `PdfThumbnailLoaderTest.kt:21` and `VersionUtilsTest.kt:179`. `SecurePrefs` branches only on `>= N`, `PdfThumbnailLoader` has no SDK check, `VersionUtils.kt:27` branches on `>= P`, which SDK 36 satisfies.

**Do:** Delete the three pins. If a class fails, fall back to SDK 26, never 27/28. Keep `VersionUtilsTest.kt:161` (pre-P) and `NotificationUtilsTest` pins. Leave `robolectricSdkJars` alone until this lands; drop the 27/28 jars in a follow-up only if nothing else needs them.

**Acceptance:** `grep -rn 'sdk = \[\(27\|28\|Build.VERSION_CODES.O_MR1\|Build.VERSION_CODES.P\)\]' app/src/test` is empty; suite green.

**Adjudication:** Removing the explicit `sdk = [36]` pins (Devin) is cosmetic — they equal the default.

### C3 · UrlUtilsTest: mock UrlUtils only in the two tests that stub it

**Rating 65** (E 80 · I 50 · F 75) · Sources: C T3 (64) · X R008 (86) · D #7 (85)

**Files:** `test: utils/UrlUtilsTest.kt`

**Verified on master:** Robolectric class; `setUp` calls `mockkObject(UrlUtils)` (:34), `tearDown` `unmockkAll()` (:43), and nine tests call `unmockkObject(UrlUtils)` (:392…:495) just to reach the real object.

**Do:** Drop the class-wide `mockkObject`; mock inside the two `getUrl()`-stubbing tests (try/finally); delete the nine `unmockkObject` calls; keep `resetForTesting`/init per test; unmock the in-test `Uri` static locally.

**Acceptance:** All 44 tests pass; before/after `<testsuite time>` in the PR.

**Adjudication:** Devin's recipe ('hoist to class level' in `@Before`) is self-contradictory.

### C4 · DownloadServiceTest: mock the three statics once per class

**Rating 63** (E 80 · I 50 · F 65) · Sources: C T4 (61) · X R020 (83) · D #10 (84)

**Files:** `test: services/DownloadServiceTest.kt`

**Verified on master:** `setUp` re-runs `mockkStatic(ContextCompat)`, `mockkObject(DownloadUtils)`, `mockkStatic(Log)` (:63-65); `tearDown` `unmockkAll()` (:75). Two tests add `mockkStatic(WorkManagerImpl)` (:127, :157).

**Do:** `@BeforeClass`/`@AfterClass` for the three; keep `Log` stubs and the `SDK_INT` restore per test; `clearAllMocks()` per test; every in-test static gets its own `unmockkStatic` in `finally`.

**Acceptance:** All 11 tests pass; class time drops (record it).

**Adjudication:** Devin names `NotificationUtils`/`ServiceDependenciesEntryPointKt`/`UrlUtils` — those are not the mocks in this file.

### C5 · docs/TESTING.md: correct the Log-mocking advice; document mock-once-per-class

**Rating 62** (E 90 · I 35 · F 90) · Sources: C T33 (59) · X R046 (75) · D #68 (71)

**Files:** `docs/TESTING.md`

**Verified on master:** `TESTING.md:341` and `:468` say tests crash without `mockkStatic(Log)`, but `app/build.gradle:22` sets `unitTests.returnDefaultValues = true`. The worker example (:320-339) teaches the per-test churn pattern.

**Do:** Verify on one class (remove its Log mock, run it); fix both paragraphs; say when a Log mock is still needed (asserting log calls); rewrite the example to class-level mocks + `clearAllMocks()`; add a 'mock once per class' subsection.

**Acceptance:** Citations resolve at the merge commit (doc-only: only the labels check runs).

**Adjudication:** Land after C3/C4 so the doc points at real examples.

### C6 · UploadConfigsTest: hoist VersionUtils/NetworkUtils object mocks

**Rating 61** (E 80 · I 40 · F 80) · Sources: C T5 (59) · X R017 (84) · D #16 (82)

**Files:** `test: services/upload/UploadConfigsTest.kt`

**Verified on master:** Plain JVM; `mockkObject(VersionUtils)` (:46) and `mockkObject(NetworkUtils)` (:49) per test, `unmockkAll()` at :75.

**Do:** Mock once per class; keep stubs and `UploadConfigs` construction in `@Before`; `clearAllMocks()` in `@After`.

**Acceptance:** All 18 tests pass; timings recorded.

### C7 · FeedbackTest/MeetupTest: delete the dead TextUtils mock, hoist NetworkUtils

**Rating 57** (E 80 · I 35 · F 80) · Sources: C T6 (55) · X R039 (79) · D #18 (81) + #51 (74)

**Files:** `test: model/FeedbackTest.kt`, `test: model/MeetupTest.kt`

**Verified on master:** FeedbackTest runs `mockkStatic(TextUtils)` (:26) and `mockkObject(NetworkUtils)` (:31) per test; `Feedback.kt` never references `TextUtils`. MeetupTest repeats the `NetworkUtils` pattern (:28/:34). The `NetworkUtils` mock is needed because `addDocumentOrigin` defaults to `NetworkUtils.getUniqueIdentifier()`.

**Do:** Delete the TextUtils block (restore + hoist if anything fails); mock `NetworkUtils` once per class with `clearAllMocks()` per test; keep the in-test `JsonParser` statics with local cleanup.

**Acceptance:** 15 + 11 tests pass; timings recorded.

### C8 · MyLibraryDaoTest: sparse boundary fixtures instead of 1200-row inserts

**Rating 57** (E 80 · I 35 · F 75) · Sources: C T14 (53) · X R011 (85) · D #6 (85)

**Files:** `test: data/room/dao/MyLibraryDaoTest.kt`

**Verified on master:** Dense 1200-row fixtures at :39, :59, :81, :103, :206, :383; the DAO wrappers chunk at **900** (`MyLibraryDao.kt:40…213`).

**Do:** Keep 1200-key requests; seed only indices 1/900/901/1200; assert exact ids and sizes; drop the redundant dense course and offline-status cases. Keep a fresh DB per test.

**Acceptance:** All chunk-boundary assertions hold; class time before/after.

**Adjudication:** Devin's shared-DB half is not part of this task, and its 999 boundary is wrong.

### C9 · Sparse boundary fixtures in seven more DAO tests (one PR per file)

**Rating 53** (E 80 · I 30 · F 75) · Sources: C T15–T21 (35–51) · X R022–R028 (83) · D #27,#28,#31–#34,#41 (76–79)

**Files:** `test: data/room/dao/{ExamDao,TeamLogDao,TeamTaskDao,AnswerDao,RemovedLogDao,CourseProgressDao,NewsDao}Test.kt`

**Verified on master:** All seed 1000–1300 rows to cross a chunk boundary. Limits: **900** everywhere except `CourseProgressDao` (**250** tuples at :40, **899** ids at :18).

**Do:** Per file — keep full-size request lists, seed only boundary rows, add an unrequested control row:
  - **ExamDaoTest** (:37, :195, :211, :227, :242): 1/900/901/1200; drop redundant `getByCourseIds_handles1200Courses`; keep the team-owned dedup assertion.
  - **TeamLogDaoTest**: boundary rows; replace the 100 old visits with one at and one below the cutoff; add wrong-type/unrequested controls.
  - **TeamTaskDaoTest** (:37, :54, :71): same recipe + an unrequested false-flag row for `markTasksNotified`.
  - **AnswerDaoTest** (:45, :59, :91): 1/900/901/(1000|1200); deletion count of 4; unrequested survivor.
  - **RemovedLogDaoTest** (:86, :110, :127): boundary targets; keep wrong-user/type controls; add a same-partition survivor.
  - **CourseProgressDaoTest** (:92, :108, :121): 1/250/251/300 and 1/899/900/1200; wrong-user controls; null-user scoping.
  - **NewsDaoTest** (:327): 1/900/901/1200 with duplicate underscore ids.

**Acceptance:** Each class: both chunks still exercised, exact identities asserted, collateral rows survive; class time before/after.

### C10 · Delete the assertion-free StepExamBenchmarkTest

**Rating 52** (E 95 · I 20 · F 100) · Sources: C T10 (48) · X R034 (81) · D #1 (93)

**Files:** `test: model/StepExamBenchmarkTest.kt`

**Verified on master:** Zero assertions: builds 500 docs, runs `insertCourseStepsExams` 10×, prints `Benchmark result` (:62). `StepExamTest` keeps 8 real tests; nothing references the benchmark.

**Do:** Delete the file. Leave `StepExamTest` and the asserting `*BenchmarkTest` repository classes.

**Acceptance:** Suite green with exactly one fewer test.

**Adjudication:** All four agent families proposed it — the only unanimous task. Devin ranked it #1 overall; impact is small, but it's a free warm-up PR.

### C11 · DownloadUtilsTest: hoist the three object mocks

**Rating 51** (E 80 · I 30 · F 65) · Sources: C T7 (49) · X R041 (77) · D #17 (81)

**Files:** `test: utils/DownloadUtilsTest.kt`

**Verified on master:** `mockkObject(Utilities)` (:52), `mockkObject(DownloadService.Companion)` (:55), `mockkObject(DownloadUtils, recordPrivateCalls=true)` (:57) per test + `unmockkAll()` (:64). Two tests call `unmockkObject(DownloadUtils)` (:208, :228) to reach real code.

**Do:** Mock once per class; keep `resetChannelsCreatedForTesting()` per test; replace the two in-test `unmockkObject` with `clearMocks(DownloadUtils)` and re-install private-call answers where needed.

**Acceptance:** All 15 tests pass.

### C12 · BasePermissionActivityTest: hoist ContextCompat/Process statics

**Rating 48** (E 75 · I 25 · F 75) · Sources: C T8 (46) · X R040 (78) · D #44 (76)

**Files:** `test: base/BasePermissionActivityTest.kt`

**Verified on master:** `mockkStatic(ContextCompat)` and `mockkStatic(Process)` (:31-32) per test, unmocked at :38-39. In-test `NotificationManagerCompat`/`ActivityCompat` statics are self-contained.

**Do:** Class-level statics and `myUid` stub; relaxed activity mock stays in `@Before`; keep cleanup of the in-test statics.

**Acceptance:** All 12 tests pass.

### C13 · NotificationActionReceiverTest: hoist NotificationUtils/entry-point statics

**Rating 47** (E 75 · I 25 · F 65) · Sources: C T9 (44) · X R042 (76) · D #43 (76)

**Files:** `test: services/NotificationActionReceiverTest.kt`

**Verified on master:** `mockkObject(NotificationUtils)` (:65) and `mockkStatic("…ServiceDependenciesEntryPointKt")` (:68) per test, `unmockkAll()` at :87; in-test `mockkStatic(Log)` at :174.

**Do:** Mock once per class; stubs, spies and Hilt reflection per test; give the in-test `Log` its own `unmockkStatic` in `finally`.

**Acceptance:** All 7 tests pass.

### C14 · Round-trip DB tests: one in-memory AppDatabase per class

**Rating 47** (E 80 · I 25 · F 60) · Sources: C T23 (46) · X R072 (65) · D #30 (78)

**Files:** `test: data/room/AppDatabaseRoundTripTest.kt`, `test: data/room/CollapsedEntitiesRoundTripTest.kt`

**Verified on master:** Both build the full 38-entity schema in `@Before` (6 + 8 = 14 builds).

**Do:** Build once per class, `clearAllTables()` per test, close in `@AfterClass`. Verify it survives Robolectric's per-test application reset before keeping it.

**Acceptance:** Both classes pass; no test sees a sibling's rows.

**Adjudication:** Conflicts with the house rule 'fresh DB per test' — state the trade-off in the PR.

### C15 · test_timing_summary.py: enforceable budgets

**Rating 45** (E 70 · I 20 · F 85) · Sources: C T28 (41) · X R029 (83) · D #60 (72) + #61 (72)

**Files:** `.github/scripts/test_timing_summary.py`

**Verified on master:** Only `--warn-over`/`--warn-class-over` exist (:64-67) and neither changes the exit code. (Copilot's claim that green runs show no timing data is false: the step runs `if: always()`.)

**Do:** Add `--fail-over` (summed) and `--fail-class-over` (per class) → exit 1; optional per-package rollup and an always-printed budget footer. Stdlib only. Document that totals sum test seconds across forks, not job wall time. Don't wire it into `test.yml` in the same PR.

**Acceptance:** Exit 1 over budget, 0 under; existing invocation output unchanged; malformed/missing XML handled.

### C16 · DashboardElementActivityNavigationTest: one activity instead of four

**Rating 44** (E 70 · I 25 · F 55) · Sources: C T13 (41) · X R071 (66) · D #56 (74)

**Files:** `test: ui/dashboard/DashboardElementActivityNavigationTest.kt`

**Verified on master:** Each of the four tests calls `Robolectric.buildActivity(…).setup()` (:55, :68, :87, :113).

**Do:** Share one activity within one Hilt test invocation; pop the back stack + `executePendingTransactions()` between scenarios; destroy explicitly afterwards. Keep all four assertions.

**Acceptance:** All four navigation assertions hold.

### C17 · SyncTimeLoggerTest/UtilitiesTest/WebViewSafetyTest: hoist repeated statics

**Rating 43** (E 75 · I 20 · F 70) · Sources: C T24 (41) · X R052 (72) · D #42 (76)

**Files:** `test: utils/SyncTimeLoggerTest.kt`, `test: utils/UtilitiesTest.kt`, `test: utils/WebViewSafetyTest.kt`

**Verified on master:** SyncTimeLoggerTest calls `mockkStatic(Log)` in 7 test bodies; UtilitiesTest mocks `Looper`/`Toast` (:28-29) and WebViewSafetyTest `Uri` (:20) in `@Before` with `unmockkAll()`.

**Do:** Mock once per class, stubs per test. Try removing the `Log` mocks outright first (`unitTests.returnDefaultValues = true`, see C5).

**Acceptance:** Assertions unchanged; no static mock leaks across classes.

### C18 · VersionUtilsTest: delete three tests subsumed by the cache tests

**Rating 43** (E 85 · I 15 · F 90) · Sources: C T22 (40) · X R062 (69) · D #76 (70)

**Files:** `test: utils/VersionUtilsTest.kt`

**Verified on master:** `…should_return_0_on_NameNotFoundException` (:147) ⊂ `…does_not_cache_NameNotFoundException` (:62); likewise :196 ⊂ :120 and :210 ⊂ :90.

**Do:** Delete the three methods; keep the SDK-branch and nullable tests.

**Acceptance:** Suite green with 3 fewer Robolectric tests.

**Adjudication:** Same file as C2, different hunks — land after it.

### C19 · test_timing_summary.py: flag likely per-test static-mock churn

**Rating 38** (E 70 · I 15 · F 75) · Sources: C T29 (36) · X R064 (68) · D #67 (71)

**Files:** `.github/scripts/test_timing_summary.py`

**Verified on master:** The script ranks slow classes but never says why; the `@Before` mock + `@After unmockkAll()` pattern is common (28 files mock `Log`).

**Do:** Optional source-sniffing section for classes with ≥ 5 tests and median ≥ 0.30 s, labelled as a heuristic.

**Acceptance:** Lists `UrlUtilsTest` before C3 lands and drops it after.

**Adjudication:** Same file as C15 — land after it.

### C20 · Adapter tests: make AsyncListDiffer synchronous instead of Thread.sleep polling

**Rating 37** (E 75 · I 15 · F 60) · Sources: C T25 (32) + T26 (26) · X dropped · D #15 (82)

**Files:** `ui/chat/ChatHistoryAdapter.kt`, `ui/user/AchievementsAdapter.kt`, `ui/teams/resources/TeamResourcesAdapter.kt`, `tests: ChatHistoryAdapterTest, AchievementsAdapterTest, TeamResourcesAdapterTest`

**Verified on master:** `Thread.sleep(10)` loops at `AchievementsAdapterTest.kt:84,111`, `ChatHistoryAdapterTest.kt:55`; `TeamResourcesAdapterTest` (:105-108) polls against wall clock. `AsyncListDiffer` diffs on its own executor, so idling Robolectric's loopers doesn't finish it (Codex's reason for dropping the 1 ms-yield recipes).

**Do:** Give each `ListAdapter` an optional `AsyncDifferConfig` constructor parameter (default unchanged); tests pass `AsyncDifferConfig.Builder(DIFF).setBackgroundThreadExecutor(Executor(Runnable::run)).build()`, then `shadowOf(getMainLooper()).idle()`. Delete the sleep loops.

**Acceptance:** No `Thread.sleep` or wall-clock deadline left in the three tests; assertions unchanged.

**Adjudication:** **New recipe.** Both source recipes (fewer/shorter sleeps) only shrank the flake window; this removes it.

### C21 · Three ViewModel tests: use the existing MainDispatcherRule

**Rating 37** (E 85 · I 10 · F 90) · Sources: C T32 (32) · X R073 (65) · D #70 (70)

**Files:** `test: ui/chat/ChatViewModelTest.kt`, `test: ui/resources/ResourcesViewModelTest.kt`, `test: ui/surveys/SurveysViewModelTest.kt`

**Verified on master:** All three hand-roll `Dispatchers.setMain/resetMain` (:56/:70, :41/:51, :46/:66).

**Do:** `@get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)`, reusing each fixture's dispatcher; drop unused imports.

**Acceptance:** All three pass unchanged.

**Adjudication:** Code health, not speed. ChatViewModelTest is also touched by B4 and D7.

### C22 · build.yml: retry once on stale incremental-compile caches

**Rating 36** (E 60 · I 15 · F 75) · Sources: C T30 (34) · X R036 (80) · D #98 (55)

**Files:** `.github/workflows/build.yml`

**Verified on master:** `build.yml` lacks `test.yml`'s `Incremental compilation failed` retry (:73) and its `!test-results`/`!reports` cache exclusions (:47-48).

**Do:** Mirror the tee + grep + `rm -rf app/build/kotlin` + single retry; preserve the first failure's status for any other error. Add the two exclusions for parity (near no-op: `assemble*Debug` produces no test results).

**Acceptance:** YAML parses; a stale cache triggers exactly one retry; other failures still fail.

**Adjudication:** Re-check open-PR ownership of `build.yml` first (three source lists flagged #15226).

---

## Wave D — Architecture boundaries and hygiene

Narrower dependencies, logic out of fragments, dead code out. Low risk, low urgency.

### D1 · Bell dashboard: one mapping-aware probe in the ViewModel

**Rating 50** (E 85 · I 25 · F 75) · Sources: C R4 (43) · X R049 (73) · D #90 (66)

**Files:** `ui/dashboard/BellDashboardFragment.kt`, `ui/dashboard/BellDashboardViewModel.kt`

**Verified on master:** The fragment reads prefs, runs `serverUrlMapper.processUrl` and probes primary then alternative (:109-136) — a double probe, because `checkServerConnection` → `ServerReachabilityProvider.isServerReachable` already tries the alternative.

**Do:** Call `viewModel.checkServerConnection(serverUrl)` once and render its result; drop the fragment's `serverUrlMapper`.

**Acceptance:** Indicator green when reachable, yellow otherwise; one probe per check.

**Adjudication:** Land after B7 so the single probe is also the concurrent one.

### D2 · ActivitiesRepository: own search-activity writes; drop the dead offlineActivityDao

**Rating 49** (E 85 · I 25 · F 65) · Sources: C R3 (46) · X R056 (71) · D #86 (66)

**Files:** `repository/ActivitiesRepository.kt + Impl`, `repository/CoursesRepositoryImpl.kt`, `repository/ResourcesRepositoryImpl.kt`, `repository/UserRepositoryImpl.kt`

**Verified on master:** `SearchActivityDao` is injected into Courses (:67, insert :341), Resources (:59, insert :477) and Activities (:60). `UserRepositoryImpl.offlineActivityDao` (:83) is never used; `removedLogDao` is used only at :1259-1260.

**Do:** Add `recordSearchActivity`/`getRemovedDocIds` to `ActivitiesRepository`; delegate; remove dead injections. Keep timestamps and payloads.

**Acceptance:** Searches still write rows; shelf still excludes removed docs.

**Adjudication:** Five files, all large `*Impl`s — re-check open-PR ownership.

### D3 · Share primary→alternative probing between ServerReachabilityWorker and SubmissionsUploader

**Rating 47** (E 80 · I 25 · F 60) · Sources: C R5 (44) · X R045 (75) · D #52 (74)

**Files:** `services/ServerReachabilityWorker.kt`, `services/SubmissionsUploader.kt`, `services/sync/ServerUrlMapper.kt`

**Verified on master:** Both run processUrl → 15 s primary probe → alternative probe → `updateUrlPreferences` (Worker :170-189, Uploader :26-72) and have drifted on total failure.

**Do:** Extract `probeWithFallback` into `ServerUrlMapper` returning an explicit result. Keep each caller's failure policy: the worker still uploads, the uploader skips.

**Acceptance:** Both paths fall back and rewrite prefs identically to today.

**Adjudication:** `ServerUrlMapper` is flagged as touched by open KMP PRs.

### D4 · DashboardViewModel: retire the expired 2024 challenge prompt

**Rating 46** (E 90 · I 20 · F 70) · Sources: C R17 (21) (note) · X dropped · D #87 (66)

**Files:** `ui/dashboard/DashboardViewModel.kt`

**Verified on master:** `evaluateChallengeDialog` (:286-340) hard-codes `startTime = 1730419200000` / `endTime = 1734307200000` (2024-11-01 → 2024-12-16) and a fixed course id, yet fans out four repository queries on every dashboard open before the date gate (:315) can say no.

**Do:** **Product decision first.** Either delete the campaign code, or at minimum move the date check before the `coroutineScope { async … }` fan-out so expired campaigns cost nothing.

**Acceptance:** Dashboard open performs zero challenge queries outside the window.

**Adjudication:** **New task.** Devin proposed extracting this into a use case, which would preserve dead work; Claude spotted the expiry. Codex dropped the extraction. DashboardViewModel is in open PR #17811.

### D5 · Delete the unreachable diff-refresh path from RealtimeSyncMixin

**Rating 44** (E 95 · I 15 · F 85) · Sources: C P16 (40) · X R058 (71) · D #23 (80)

**Files:** `ui/sync/RealtimeSyncMixin.kt`, `callback/OnDiffRefreshListener.kt`, `ui/resources/ResourcesFragment.kt`, `ui/courses/CoursesFragment.kt`

**Verified on master:** No class implements `OnDiffRefreshListener`; both mixins (`ResourcesFragment.kt:800`, `CoursesFragment.kt:566`) return `shouldAutoRefresh = false`.

**Do:** Delete the interface, `getSyncRecyclerView`, `shouldAutoRefresh`, `refreshRecyclerView` and their overrides. Keep `onDataUpdated` and the flow filters.

**Acceptance:** Builds; lists still refresh via `onDataUpdated`.

**Adjudication:** Check open-PR ownership of `ResourcesFragment.kt` (#13355 flagged).

### D6 · HealthRepository: own examination-signature encryption

**Rating 39** (E 80 · I 15 · F 65) · Sources: C R6 (36) · X R047 (74) · D #89 (66)

**Files:** `ui/health/HealthExaminationViewModel.kt`, `repository/HealthRepository.kt + Impl`

**Verified on master:** The VM's private `encryptSign` (:128-141) mutates the user's key/IV and the examination before `healthRepository.saveExamination` (:116).

**Do:** Extend `saveExamination(…, sign)` and move the body; keep catch-and-log behaviour and encrypt-before-persist ordering.

**Acceptance:** Saved exams still decrypt.

**Adjudication:** Pulls `AndroidDecrypter` into the data layer — acceptable now, revisit if the repository layer goes platform-free.

### D7 · Narrow five ViewModels from UserRepository to UserReadRepository (one PR each)

**Rating 38** (E 90 · I 10 · F 95) · Sources: C R10–R14 (34) · X R054, R065–R068 (68–72) · D #84 (66)

**Files:** `ui/teams/members/RequestsViewModel.kt`, `ui/chat/ChatViewModel.kt`, `ui/resources/AddResourceViewModel.kt + ResourceDetailViewModel.kt`, `ui/courses/ProgressViewModel.kt + CourseProgressViewModel.kt`, `ui/feedback/FeedbackComposerViewModel.kt`, + their tests

**Verified on master:** Each injects `UserRepository` but only calls `getUserModel()`/`getUserById()`. `UserReadRepository` exists and is bound (`RepositoryModule.kt:229`). `ChatViewModelTest` also declares broad `TeamsRepository`/`VoicesRepository` mocks (:46-47) although the constructor takes the `*ShareRepository` types.

**Do:** Change constructor types and test mocks. Keep ProgressViewModel's local-id vs CourseProgressViewModel's remote-`_id` forwarding.

**Acceptance:** Each test class passes; no DI change needed.

**Adjudication:** ChatViewModel overlaps B4 and C21 — sequence.

### D8 · Route survey/progress submission and exam reads through SubmissionsRepository

**Rating 38** (E 80 · I 15 · F 60) · Sources: C R7 (35) · X R063 (68) · D #85 (66)

**Files:** `repository/SubmissionsRepository.kt + Impl`, `repository/SurveysRepositoryImpl.kt`, `repository/ProgressRepositoryImpl.kt`

**Verified on master:** SurveysRepositoryImpl reads `submissionDao` at :207-209, :279, :301-303, :353, :369; ProgressRepositoryImpl uses `examDao`/`questionDao` at :65, :186.

**Do:** Add read passthroughs, swap the reads, remove a DAO dependency only when its last use is gone; `dagger.Lazy` to avoid a cycle (Submissions already depends on Teams).

**Acceptance:** Survey list, badges and `fetchCourseData` unchanged.

**Adjudication:** CLAUDE.md lets each Impl inject the DAOs it needs, so this is a design choice, not a defect. Do it only if the team wants this boundary.

### D9 · Replace printStackTrace with tagged Log.e (three PRs)

**Rating 34** (E 90 · I 8 · F 90) · Sources: C P23–P25 (29–31) · X R077–R079 (60) · D #92–#94 (64)

**Files:** `base/BaseTeamFragment.kt (:62)`, `base/BaseContainerFragment.kt (:173, :175)`, `utils/FileUtils.kt (:255)`, `utils/KeyboardUtils.kt (:16)`, `ui/exam/ExamTakingFragment.kt (:142, :177, :664)`

**Verified on master:** Raw `printStackTrace` at the listed lines.

**Do:** Stable `TAG` + operation-specific `Log.e(TAG, msg, e)`. **Leave `RetryUtils.kt:31` alone**: it returns `null`, so that trace is the only diagnostic, and the file has no Android imports.

**Acceptance:** `grep printStackTrace` empty in the touched files; fallbacks unchanged.

**Adjudication:** Claude would delete the RetryUtils line; Codex keeps it — Codex is right. ExamTakingFragment is in open PR #15559.

### D10 · Share the voices draft-image entry builder

**Rating 33** (E 80 · I 10 · F 65) · Sources: C R15 (30) · X R060 (70) · D #88 (66)

**Files:** `base/BaseVoicesFragment.kt`, `ui/voices/ReplyActivity.kt`, `model/News.kt`

**Verified on master:** Duplicated `imageUrl`/`fileName` JSON building and duplicate checks (BaseVoicesFragment :157-184, ReplyActivity :217-238).

**Do:** `News.createImageListEntry` / duplicate-path helper used by both.

**Acceptance:** Duplicate images still rejected in both screens.

**Adjudication:** All three files changed this week and sit in voices open-PR territory.

### D11 · EditAchievementFragment: move entry JSON assembly into Achievement/AchievementViewModel

**Rating 31** (E 75 · I 10 · F 55) · Sources: C R16 (28) · X R055 (71) · D #91 (66)

**Files:** `ui/user/EditAchievementFragment.kt`, `ui/user/AchievementViewModel.kt`, `model/Achievement.kt`

**Verified on master:** The fragment owns `achievementArray` and mutates it at :118, :268-281, :382, :413, :454; `Achievement.createReference` (:156) is the precedent.

**Do:** Add `Achievement.createEntry`; hold the list in the VM (survives recreation); dialogs stay in the fragment.

**Acceptance:** Create/edit preserves entries, resources and references across rotation.

### D12 · DictionaryRepositoryImpl: trim at the repository boundary

**Rating 27** (E 80 · I 5 · F 85) · Sources: C R18 (23) · X R053 (72) · D #82 (67)

**Files:** `repository/DictionaryRepositoryImpl.kt`, `ui/dictionary/DictionaryViewModel.kt`, + tests

**Verified on master:** The VM trims (:58-61), the repository doesn't (:25-26); the VM is the only caller, so this is hypothetical today.

**Do:** Trim + blank-guard in the repository. **Don't lowercase** — the DAO match is case-exact by design.

**Acceptance:** Padded words found; blank input doesn't query.

**Adjudication:** Devin's `lowercase()` would change lookup semantics.

### D13 · UserInformationFragment: one TAG, drop happy-path Log.d noise

**Rating 25** (E 85 · I 4 · F 85) · Sources: C P26 (21) · X R080 (59) · D #95 (63)

**Files:** `ui/exam/UserInformationFragment.kt`

**Verified on master:** Ten `Log.d("UserInformationFragment", …)` literals (:92…:296).

**Do:** One `TAG`; delete or downgrade happy-path logs; keep failure diagnostics.

**Acceptance:** One TAG; behaviour unchanged.

---

## Wave E — Optional micro-optimisations

Only when you are already in the file. None of these is worth a PR on its own merits.

### E1 · Bound the per-adapter date-string caches

**Rating 37** (E 85 · I 10 · F 90) · Sources: C — · X R050 (73) · D #55 (74)

**Files:** `ui/{personals/PersonalsAdapter, feedback/FeedbackAdapter, health/HealthUsersAdapter, user/UserArrayAdapter, teams/TeamsAdapter, events/EventsAdapter}.kt`

**Verified on master:** Six adapters keep an unbounded `dateCache` map of timestamp → string (both plans found only four; `TeamsAdapter.kt:28` and `EventsAdapter.kt:38` also have one). Growth is bounded by distinct timestamps in the list, so this is hygiene.

**Do:** Swap in a small `LruCache<Long, String>` (e.g. 256); clear on locale change.

**Acceptance:** Same formatted dates; no unbounded maps.

### E2 · DashboardActivity: reuse the TabLayout reference in topBarVisible

**Rating 37** (E 85 · I 12 · F 70) · Sources: C P22 (34) · X R069 (67) · D #96 (62)

**Files:** `ui/dashboard/DashboardActivity.kt`

**Verified on master:** `OnGlobalLayoutListener { topBarVisible() }` (:278-279) runs `findViewById<TabLayout>` each pass (:800-808).

**Do:** Use the existing `tl` reference; skip writing unchanged visibility.

**Acceptance:** Tab bar hides in portrait, shows in landscape.

**Adjudication:** In open PR #15825.

### E3 · TagEntity.getTagsArray: build the Gson JsonArray directly

**Rating 27** (E 85 · I 5 · F 85) · Sources: C P35 (24) · X R059 (71) · D #80 (68)

**Files:** `model/TagEntity.kt`

**Verified on master:** `buildJsonArray{…}.toGson()` (:63-67) builds two trees.

**Do:** Build `JsonArray` directly; preserve order, duplicates and nulls.

**Acceptance:** Identical payloads (`GsonUtilsKotlinxBridgeTest`).

### E4 · TeamPagerAdapter.updatePages: early-return on equal pages

**Rating 27** (E 85 · I 5 · F 85) · Sources: C P36 (24) · X R061 (70) · D #49 (75)

**Files:** `ui/teams/TeamPagerAdapter.kt`

**Verified on master:** Always diffs (:37-49); one caller, ≤ ~10 tabs.

**Do:** Return when the new configuration equals the current one.

**Acceptance:** Stable ids unchanged.

### E5 · UsersAdapter: set the click listener once in the ViewHolder

**Rating 27** (E 80 · I 5 · F 85) · Sources: C P33 (23) · X R076 (61) · D #83 (part)

**Files:** `ui/user/UsersAdapter.kt`

**Verified on master:** `setOnClickListener` per bind (:31-37); HealthUsersAdapter already does it in `init`. (The avatar-size half of the source order is already cached.)

**Do:** Move into `init` with `bindingAdapterPosition` + NO_POSITION guard.

**Acceptance:** Taps open the right user.

### E6 · ResourcesViewModel title sort: sort the decorated list in place

**Rating 25** (E 85 · I 4 · F 85) · Sources: C P37 (21) · X R070 (67) · D #81 (68)

**Files:** `ui/resources/ResourcesViewModel.kt`

**Verified on master:** `map{…}.sortedBy{…}.map{…}` (:203-205) — one extra list.

**Do:** Pre-sized mutable list + stable in-place sort.

**Acceptance:** Tie order preserved both directions.

**Adjudication:** Same file as B11 — sequence.

### E7 · ResourcesMediaType.classify: bounded memo (measure first)

**Rating 23** (E 65 · I 5 · F 60) · Sources: C P29 (20) · X R081 (58) · D #63 (72)

**Files:** `ui/resources/ResourcesMediaType.kt`

**Verified on master:** `classify`/`extensionOf` lowercase and parse per call (:20-29, :69-73); a three-string cache key costs about the same to hash.

**Do:** Only if profiling shows it: small access-ordered map keyed by the triple.

**Acceptance:** Every existing classify case returns the same enum.

### E8 · SurveysAdapter: resolve labels and guest flag once

**Rating 21** (E 70 · I 3 · F 85) · Sources: C P32 (18) · X R082 (58) · D #77 (part) (69)

**Files:** `ui/surveys/SurveysAdapter.kt`

**Verified on master:** One of three mutually exclusive `getString` calls + `startsWith("guest")` per bind (:109-117).

**Do:** Hoist to holder creation / constructor.

**Acceptance:** Labels unchanged.

---

## Rejected proposals

Proposed by at least one plan and not carried forward. Reasons come from the plan that rejected the task (mostly Codex's verification); † = re-checked on master for this merge.

| Proposal | Proposed by | Why not |
|---|---|---|
| Delete the two `CourseRatingUtilsTest` widget smoke tests † | Claude T11, Devin #4 | They `verify { average.text = "4.50" }`, `ratingCount.text`, `ratingBar.rating` — real assertions the pure tests don't cover (checked on master). |
| Delete the two `ViewExtensionsTest` `textChanges` tests | Claude T12, Devin #22 | Only coverage of production `textChanges` (used by TeamFragment, SubmissionsFragment, VoicesFragment). |
| Classify `UnknownHost`/`SSLHandshake` as permanent in `RetryInterceptor` † | Claude P1, Devin #2 | Transient on flaky networks; replaced by B3 (fail fast only with no network). Devin's 4xx premise is false. |
| Debounce the `exams`/`chats`/`teams` realtime collectors † | Codex R031/R035/R037, Devin #58 | Nothing emits those tables: the only producer is `CoursesRepositoryImpl` emitting `"courses"`. Dead until a producer exists. |
| Cache facet results by content signature | Codex R074, Claude P31 (skip), Devin #46 | The signature is an O(n) walk of the same fields the rebuild walks. |
| Memoise lowercased collection tag names | Codex R075, Claude P34 (skip), Devin #79 | Tiny lists; the cache costs more code than it saves. |
| WebView night mode via injected stylesheet / `filter: invert(1)` | Claude P14, Devin #35 | Today's script recolours only exact black/white; a stylesheet changes author colours and `invert` flips images. Spike with real resources first if wanted. |
| Cache `File.exists()` in `InlineResourceAdapter` | Claude P21, Devin #53 | Goes stale after download/delete while the row stays. |
| Memoise `findHtmlCoverImage` hits | Claude P27, Devin #64 | Both callers already cache per library id; can miss replaced covers. |
| ChatAdapter append without copying | Claude P28 (skip), Devin #50 | `ListAdapter` requires immutable snapshots; the copy is O(n) references. |
| ExamAnswerUtils normalisation hoist | Claude P30, Devin #65 | Branches are alternatives (one locale read per call); runs once per submission. |
| `runBlocking` → `runTest` across DAO/round-trip tests | Devin #71–#74 | Real Room executors, no delays to virtualise — no speed win. |
| Delete `LifeRepositoryTest` | Devin #75 | Its non-matching-id case (zero DAO updates) isn't covered elsewhere. |
| Delete `RealtimeSyncManagerTest` no-collector case | Devin #25 | Calling `notifyTableUpdated` without collectors is a valid no-throw test; only the `assertTrue(true)` line is redundant. |
| `@Singleton` on DAO providers | Devin #97 | Room's generated `AppDatabase_Impl` already caches each DAO lazily. |
| `NOT LIKE 'guest%'` in `NewsDao` | Devin #57 | SQLite `LIKE` is case-insensitive: drops `Guest…` ordinary users; Kotlin `startsWith` is case-sensitive. |
| ResourcesCardBinder decode bound / double stat | Devin #40 | Glide already sizes from the target; the PDF path doesn't stat twice. |
| NotificationsAdapter / TeamsSelectionAdapter / ChatShareTargetAdapter / PersonalsAdapter bind work | Devin #77, #78, #83 (parts) | Already cached, or runs on click not bind. |
| Shrink `SyncTimeLoggerConcurrencyTest` to 4×50 | Claude T27, Devin #47 | Weakens race coverage for little time. |
| Merge the three `DownloadService*` helper test files | Claude T31, Devin #69 | Plain JVM classes: no sandbox saved; organisation only. |
| Extract challenge-dialog evaluation into a use case † | Devin #87 | Would preserve dead work for an expired campaign; replaced by D4. |
| Thread.sleep → 1 ms yields / looper flushing | Claude T25/T26, Devin #15 | Doesn't synchronise `AsyncListDiffer`'s executor; replaced by C20. |

## Shared files and sequencing

| File / area | Order |
|---|---|
| `data/room/dao/TeamDao.kt` | A5 → B2 |
| `ui/chat/ChatViewModel.kt` + test | B4 → C21 → D7 (chat PR) |
| `ui/resources/ResourcesViewModel.kt` | B11 → E6 |
| `utils/KeyboardUtils.kt` | B8 → D9 |
| `utils/VersionUtilsTest.kt` | C2 → C18 |
| `.github/scripts/test_timing_summary.py` | C15 → C19 |
| Reachability (`ServerReachabilityProvider`, `ServerUrlMapper`, bell dashboard) | B7 → D1 → D3 |
| `ui/health/*` | A3 only (merged) |
| Open PRs flagged by source lists — re-check before starting | `DashboardActivity` #15825 (E2), `DashboardViewModel` #17811 (D4), `ExamTakingFragment` #15559 (D9), `build.yml` #15226 (C22), `ResourcesFragment` #13355 (D5), KMP PRs on `services/upload/*Test`, `ServerUrlMapper`, repository `*Impl`s |

## How the three source plans compared

| Point of view | Claude | Codex | Devin |
|---|---:|---:|---:|
| Factual accuracy | 85 | 93 | 55 |
| Judgement and risk for an offline-first app | 72 | 90 | 50 |
| Prioritisation | 78 | 82 | 50 |
| Actionability | 92 | 72 | 60 |
| Traceability | 82 | 95 | 60 |
| Signal-to-noise | 75 | 85 | 45 |
| **Overall** | 82 | 87 | 54 |

Codex's misses that this plan corrects: it kept three debounce tasks for realtime tables nobody emits, kept the facet/collection caches, rated the latent exporter bug 95, and dropped the cheap `TeamDao` narrowing instead of fixing its SQL. Claude's: the permanent-failure retry change, deleting tests that assert, and the `TRIM` whitespace gap. Devin's: see the *Adjudication* notes — most of its unique tasks are in *Rejected*.

