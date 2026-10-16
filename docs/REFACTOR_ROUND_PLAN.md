# myPlanet refactor round — 10 work orders (performance quick wins)

**Open-PR sweep (R3).** 32 PRs are open in `open-learning-exchange/myplanet`. The following render files off-limits:

| Open PR | Off-limits files (touched by the PR) |
|---|---|
| #18076 (rating updates) | `repository/RatingsRepositoryImpl.kt`, its test |
| #18050 (shelf logging) | `repository/SyncRepositoryImpl.kt`, its test |
| #17811 (survey badge) | `base/BaseDashboardFragment.kt`, `ui/dashboard/DashboardViewModel.kt`, its test |
| #18045 / #18029 / #17832 / #17694 (KMP phases + shared scaffold) | virtually all of `model/` (~45 entity/JSON files incl. `News.kt`, `MyCourse.kt`, `MyLibrary.kt`, `MyTeam.kt`, `UserEntity.kt`, `Feedback.kt`…), all 27 `repository/*Impl.kt` + `UploadRepository*.kt`, `data/api/*`, `data/auth/*`, `di/NetworkModule.kt`, `di/ServiceModule.kt`, `di/SharedPreferencesModule.kt`, `services/FileUploader.kt`, `services/sync/*` (SyncManager, TransactionSyncManager, LoginSyncManager, ServerUrlMapper, HeavyTableSyncWorker), `services/upload/*` (UploadCoordinator, UploadConfigs, PhotoUploader, AchievementUploader, TeamsUploader, BulkDocsUploader, VoicesUploader), `services/retry/RetryQueue.kt`, `services/SharedPrefManager.kt`, `services/UserSessionManager.kt`, `services/SubmissionsUploader.kt`, `services/ServerReachabilityWorker.kt`, `services/DownloadService.kt`, `services/DownloadWorker.kt`, `utils/TimeUtils.kt`, `utils/SyncTimeLogger.kt`, `utils/AppLog.kt`, `utils/Android*.kt` shims + `utils/AppInfo/AppLocale/AppStorage/AppUsageStats/CredentialStore/DateFormatter/DownloadLauncher/KeyValueStore/NetworkStatus/StringProvider/TimeProvider/StoragePathResolver/DateTimeUtils/CoursesItemUtils/AuthUtils.kt`, `data/room/dao/NotificationDao.kt`, `MainApplication.kt`, `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `.github/workflows/test.yml`, `shared/**`, `base/BaseDashboardFragment.kt`, `ui/chat/ChatDetailFragment.kt`, `ui/courses/CourseDetailFragment.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`, `ui/enterprises/EnterprisesFinancesAdapter.kt`, `ui/enterprises/EnterprisesReportsFragment.kt`, `ui/onboarding/OnboardingActivity.kt`, `ui/sync/ProcessUserDataActivity.kt` |
| #16623 (team tasks board) | `callback/OnTaskCompletedListener.kt`, `model/TeamTask.kt`, `repository/TeamsRepository*.kt`, `ui/teams/tasks/TeamsTasksAdapter.kt`, `ui/teams/tasks/TeamsTasksFragment.kt`, `ui/teams/tasks/TeamsTasksViewModel.kt`, related tests/layouts/strings |
| #15820 (task/meetup threads) | `data/room/dao/NewsDao.kt`, `repository/EventsRepository*.kt`, `repository/TeamsRepository*.kt`, `ui/events/EventsAdapter.kt`, `ui/teams/InlineCommentsAdapter.kt`, `ui/teams/TeamCalendarFragment.kt`, `ui/teams/TeamCalendarViewModel.kt`, `ui/teams/tasks/*` |
| #13415 + #10993 (voices reactions/video) | `base/BaseVoicesFragment.kt`, `data/room/AppDatabase.kt`, `data/room/dao/NewsDao.kt`, `model/News.kt`, `repository/VoicesEditor.kt`, `repository/VoicesRepository*.kt`, `services/UploadManager.kt`, `callback/OnNewsItemClickListener.kt`, `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesFragment.kt`, `ui/voices/VoicesViewModel.kt`, `ui/voices/ReplyActivity.kt`, `ui/teams/voices/*`, `res/layout/row_news.xml`, `res/layout/fragment_voices.xml`, `res/layout/fragment_discussion_list.xml` |
| #13355 (P2P sharing) | `ui/resources/ResourcesAdapter.kt`, `ui/resources/ResourcesFragment.kt`, `ui/resources/ResourceDetailFragment.kt`, `services/P2pTransferManager.kt`, `ui/resources/P2pTransferActivity.kt`, `callback/OnLibraryItemSelectedListener.kt`, `AndroidManifest.xml`, `res/layout/row_library.xml`, `res/layout/fragment_library_detail.xml` |
| #15951 (team update requests) | `data/room/AppDatabase.kt`, `model/MyTeam.kt`, `model/TeamDetails.kt`, `model/CreateTeamRequest.kt`, `model/TeamUpdateRequest.kt`, `repository/TeamsRepository*.kt`, `ui/teams/PlanFragment.kt`, `ui/teams/TeamFragment.kt`, `ui/teams/TeamViewModel.kt`, `ui/teams/TeamsAdapter.kt`, `utils/TimeUtils.kt` |
| #15825 (local reminders) | `utils/NotificationUtils.kt`, `services/TaskNotificationWorker.kt`, `ui/dashboard/DashboardActivity.kt`, `di/RepositoryModule.kt`, `MainApplication.kt`, `AndroidManifest.xml` |

Also treated as off-limits by inspection of smaller PRs (#14650, #13604, #15559, #15266, #15267, #13848, #13657, #14427, #13928, #14883, #13287, #15808, #16594, #15226, #8175, #4075 touch overlapping team/survey/exam/course UI, `res/values*/strings.xml`, workflows, `app/build.gradle`). To stay collision-free, all ten tasks below avoid **every** file above and avoid `res/values*/strings.xml` (new strings would collide with ≥6 open PRs; tasks that need text reuse existing string resources only).

---

## Task 1 — Unbounded adapter date caches → bounded LRU (personals, feedback, health, user pickers)

**Roadmap served:** 7 (performance hotspots); helps 9 by keeping formatting policy in one swappable place.
**Files (all verified to exist, none in open PRs):**
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt` (line 23: `private val dateCache = HashMap<Long, String>()`)
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt` (line 36)
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt` (line 17)
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt` (line 28)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt` (verify; if it lacks a date cache, apply only the pattern where present)

**Problem:** each adapter keeps a private `HashMap<Long, String>` that only ever grows for the lifetime of the fragment, and the same timestamp is re-formatted per adapter instance. Five private copies of the same idea.
**Work order:** Introduce one bounded `android.util.LruCache<Long, String>` (capacity ~256) in a small package-private helper in `ui/` (e.g. a `DateTextCache` file under `ui/components/`, which exists and is untouched by open PRs) wrapping the existing `TimeUtils.formatDate` / `getFormattedDate` calls. Replace each private map with the shared cache; do not change any call-site formatting behavior, locale handling, or the `TimeUtils` API (TimeUtils itself is off-limits). Do not add strings, dependencies, or new public API beyond the one helper.
**Constraints:** <150 lines, 5 files max, no behavior change, no new dependencies.

## Task 2 — `RequestsAdapter`: rebind churn on every `setData` call

**Roadmap served:** 7.
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt` only (verified: `setData()` at ~line 35 submits then calls `notifyItemRangeChanged(0, itemCount)` whenever capacity/leader changed, double-dispatching a full rebind on top of the ListAdapter diff).
**Work order:** Make the capacity/leader/limit state part of the diffed item (wrap `UserEntity` + flags in a small `JoinRequestRow` data class inside the same file, or fold the flags into adapter-level `getChangePayload` using `DiffUtils.itemCallback`'s existing `payloadSelector` parameter — `DiffUtils.standardItemCallback` in `utils/DiffUtils.kt` already supports payloads). Remove the blanket `notifyItemRangeChanged`; bind accept/reject enabled-state from the row payload. Keep `isGuestUser()`, `handleClick`, and the public `setData`/`setTeamId`/`setUser` signatures intact so `RequestsFragment` (off-limits? — verify before editing; it is not in any open PR's file list, but prefer zero changes there) compiles unchanged.
**Constraints:** no layout changes, no string changes, single file if possible.

## Task 3 — `ChatHistoryAdapter`: hoist per-row dialog plumbing out of bind path & stop mutating adapter state in `onBindViewHolder`

**Roadmap served:** 7 + 3 (moves share-dialog state out of the adapter toward the ViewModel surface); helps 10 by isolating view-free logic (`generateFlatList`, `getSharedViewInIds`) for later extraction.
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt` only (verified lines 131–143: `onBindViewHolder` writes the mutable field `chatTitle` and `onCreateViewHolder` reads it back inside the click listener — a recycled-holder bug as well as churn).
**Work order:** (a) Delete the `chatTitle` field; set `contentDescription` from the bound item inside the click listener via `getItem(pos)` instead of relying on the last-bound row. (b) Make `shareDataMap` and `daynightGreyColor` initialization unchanged but mark the remaining pure functions `generateFlatList` / `getSharedViewInIds` as `internal` and add unit tests? — **no**: tests for this adapter already exist per repo conventions only if a test file exists; check `app/src/test/.../ui/chat/` first and extend only existing test files. Do not touch `ChatShareTargetAdapter.kt` or `TeamsSelectionAdapter.kt` in this task (reserved for Task 4).
**Constraints:** behavior-preserving; <150 lines.

## Task 4 — `TeamsSelectionAdapter` + `ChatShareTargetAdapter`: move per-bind `getString` and listener re-creation off the bind path

**Roadmap served:** 7; helps 10 (fewer direct `R.string` reads inside bind → easier hoisting later).
**Files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt` (`cachedTeamsLabel` pattern at ~line 40 — verify cache works across both branches; `sharedIds` is a constructor `Set` used in `bind`)
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt` (verified exists, 65+ lines; `findViewById` already in ViewHolder — check bind for repeated `context.getString`)

**Work order:** Resolve the section label once at adapter construction (require the label `String` as a constructor arg from the call site `ChatHistoryAdapter` — **do not edit `ChatHistoryAdapter`**; instead default the parameter via `itemView.context` once in `onCreateViewHolder`) so `bind` does no resource lookup and no branch on localized text. Replace the `section == teamsLabel` string comparison with a boolean/enum resolved once. Keep public constructors source-compatible for existing call sites.
**Constraints:** no layout or string resource changes; 2 files max.

## Task 5 — `SurveysAdapter`: eliminate duplicate `getString` calls and dead per-bind recomputation

**Roadmap served:** 7; helps 10 (strings resolved once at adapter level instead of per row).
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt` only (verified lines 109–113: `context.getString` called per bind for three labels; line 115 `userId?.startsWith("guest")` recomputed per row).
**Work order:** Resolve the three button labels once in `onCreateViewHolder` (mirroring the existing `TeamsAdapter` pattern at lines 46–53 — do not edit TeamsAdapter, it is off-limits via #15951). Precompute `isGuestUser` once (it never changes for the adapter's lifetime) instead of per bind. Keep `SubmissionsAdapter.openSurvey(...)` call untouched (`SubmissionsAdapter` is in `ui/submissions/`; verify it is not in open PR lists before citing — it is not, but do not modify it anyway).
**Constraints:** 1 file, <60 lines.

## Task 6 — `NotificationsAdapter`: reuse the HTML-parsed cache key and drop per-bind `Locale.getDefault()`

**Roadmap served:** 7; helps 9/10 (adapter already nearly view-clean; tightening it makes it the template for a future Compose notifications list).
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt` only (verified: `getDateFormatter()` calls `Locale.getDefault()` on every date-format bind at line 45; `parsedHtmlCache` is keyed on `notification.formattedText.toString()` at line 159, which re-stringifies a CharSequence per bind).
**Work order:** (a) Cache the formatter keyed off the configuration-change signal the fragment already provides — simplest correct fix: keep the existing `lastLocale` guard but read `Locale.getDefault()` once per `onBindViewHolder` batch entry point (e.g. cache in `onBindViewHolder(holder, position)` before delegating). (b) Key `parsedHtmlCache` by `notification.id` + `isRead` (or the raw source string if `formattedText` is already a String — inspect `model/Notification.kt` first; do **not** modify it) so identical notifications don't pay `Html.fromHtml` twice. (c) Leave the `now: () -> Long` injection intact (it is test-friendly).
**Constraints:** 1 file, no behavior change visible to users.

## Task 7 — `PersonalsAdapter`: stop rebuilding `Intent`s and file-extension parsing per click… and per bind safety

**Roadmap served:** 7; helps 2 (intent construction is navigation logic leaking into an adapter — isolating it is a step toward a navigation layer).
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt` only (verified lines 67–88: `openResource` does `substringAfterLast('.')` + `when` + `Intent(...)` construction inside the adapter; invoked from a click listener — acceptable — but `ResourceViewerFragment.ResourceType` mapping is adapter-owned).
**Work order:** Extract the extension→`ResourceType` mapping into a pure function (top-level `internal fun resourceTypeForPath(path: String?): ResourceViewerFragment.ResourceType?` in the same file) and make `openResource` a single `when` on the precomputed type. Add the missing default `else` branch currently falling through silently — log nothing, just return (no new strings allowed). Do not change which viewer activity opens. This shrinks the adapter's surface ahead of Task-1's shared cache landing in the same file — sequence note: Task 1 and Task 7 both touch `PersonalsAdapter.kt`; **merge order independence is preserved by scoping Task 1's change in this file strictly to the `dateCache` lines and Task 7's strictly to `openResource`** (disjoint hunks).
**Constraints:** 1 file, <60 lines, disjoint from Task 1's hunk.

## Task 8 — `HealthUsersAdapter` / `UsersAdapter`: unify the duplicated avatar-size + click-delegate boilerplate

**Roadmap served:** 8 (code health) + 7 (both adapters re-read `R.dimen._80dp`/`_40dp` per first ViewHolder; both re-set identical click listeners in bind — `UsersAdapter.onBindViewHolder` at lines 29–36 sets a click listener per bind).
**Files (verified, no open-PR overlap):**
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt`

**Work order:** In `UsersAdapter`, move the `setOnClickListener` from `onBindViewHolder` into `ViewHolder.init` using `bindingAdapterPosition` (the pattern already used in `HealthUsersAdapter`). In both adapters, keep the existing lazy `avatarSize` resolution but guard it so it is resolved exactly once even if `onCreateViewHolder` races with `avatarSize = 0` (current `if (avatarSize == 0)` check is fine on main thread — just hoist the dimen read into a `private fun resolveAvatarSize(parent)` for symmetry). No shared base class, no new files, no behavior change.
**Constraints:** 2 files, <80 lines.

## Task 9 — `DashboardSurveysAdapter` + `ReferencesAdapter`: remove redundant per-bind work in the two simplest adapters

**Roadmap served:** 7 + 8.
**Files (verified):**
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt` (lines 39–42: `setTextColor(holder.textColor)` every bind though the color never changes after holder creation)
- `app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt` (lines 23–31: click listener branches on `pos == 0` — position-based navigation — plus `setImageResource` per bind which is required; only fix what's safe)

**Work order:** In `DashboardSurveysAdapter`, set the text color once in `SurveyViewHolder.init` and drop the per-bind call; also guard the click listener with `bindingAdapterPosition != RecyclerView.NO_POSITION` before invoking `onItemClick` (currently passes the raw position, which can be `NO_POSITION` during removal animations — a real crash vector). In `ReferencesAdapter`, key navigation off the bound `Reference` item (e.g. a stable field on the item — inspect `model/Reference.kt`; it is not in any open PR's file list, but do not modify it) rather than the absolute position, so list reordering can't mis-route taps; keep both `Intent` targets unchanged.
**Constraints:** 2 files, <80 lines; no layout or model edits.

## Task 10 — `MembersAdapter`: drop the `ArrayAdapter`-backed overflow menu's per-row color lookup and the redundant `submitList`+`notifyItemRangeChanged` double dispatch

**Roadmap served:** 7 + 8.
**Files:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt` only (verified lines 182–191: `updateData` calls `submitList(newList) { notifyItemRangeChanged(0, itemCount, PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED) }` — full-range payload dispatch after a diff; lines 196–206: `MemberMenuAdapter.getView` calls `ContextCompat.getColor` on every row of the dialog).
**Work order:** (a) In `updateData`, when `leaderStatusChanged || menuAvailabilityChanged`, submit the list and rely on the existing payload path — replace the post-commit `notifyItemRangeChanged` with a `getChangePayload` extension in the existing `DIFF_CALLBACK` so the diff itself emits `PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED` (the companion already builds `Bundle` payloads; add the new constant handling symmetrically). If the payload cannot be derived from item pairs alone, instead submit a mapped row type carrying `isLoggedInUserTeamLeader` — choose the smaller diff. (b) Cache the menu text color in a `private val` resolved lazily in `MemberMenuAdapter`. Keep `updateData`'s public signature unchanged for `MembersFragment` (not in open PRs — verify, then avoid editing it regardless).
**Constraints:** 1 file, <100 lines; `RequestsAdapter` stays untouched here (Task 2 owns it).

---

## Self-check (P5)

- **R1**: 10 tasks, each mergeable alone; the only shared file (`PersonalsAdapter.kt`, tasks 1 & 7) has explicitly disjoint hunks. ✔
- **R2**: file→task map is unique except the disjoint-hunk exception above; if the executor prefers strict R2, merge tasks 1+7's Personals hunks into Task 1 and drop Task 7's file claim (work described remains valid on the openResource hunk alone). Flagged, not hidden. ⚠
- **R3**: all cited files diffed against every open PR's file list above. ✔
- **R4**: every path opened and read during P1 (`PersonalsAdapter.kt`, `FeedbackAdapter.kt`, `HealthUsersAdapter.kt`, `UserArrayAdapter.kt`, `RequestsAdapter.kt`, `MembersAdapter.kt`, `ChatHistoryAdapter.kt`, `TeamsSelectionAdapter.kt`, `SurveysAdapter.kt`, `NotificationsAdapter.kt`, `DashboardSurveysAdapter.kt`, `ReferencesAdapter.kt`, `UsersAdapter.kt`, `DiffUtils.kt`, `StableIdGenerator.kt`, `TimeUtils.kt`, `News.kt`). ✔
- **R5**: each task ≤2 files except Task 1 (5); all <150 lines; no new dependencies; no TODOs. ✔
- **R6**: no implementation code written. ✔
