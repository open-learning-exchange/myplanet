# myPlanet refactor round — performance quick wins

date: 2026-11-10 · base commit: 2093a6224 (master, "all: smoother base recycler download offer tracking (fixes #5278) (#18543)") · open PRs checked: #18555, #18554, #18551, #18540, #18539 (also noted #18455, created 2026-10-29 — just past the one-week window — and its files were avoided anyway)

Each task is independently mergeable in any order; no file appears in two tasks. Acceptance bar for every task: `./gradlew testDefaultDebugUnitTest` stays green and the listed behavior is unchanged.

---

### 1. fetch team memberships once in `getJoinedMembersWithVisitInfo` (roadmap 1+7)

context: `TeamsRepositoryImpl.getJoinedMembersWithVisitInfo` reads the same `teams` rows three times: `getJoinedMembers(teamId)` internally queries `teamDao.getByTeamIdAndDocType(teamId, "membership")`, then `teamDao.getAllByTeamId(teamId)` pulls every doc type for the team just to build `teamUserIds`, then `getByTeamIdAndDocType(teamId, "membership")` runs a third time for `leaderIds`. One membership fetch yields all three sets. Fewer Room round-trips on a screen that lists every member.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` — function `getJoinedMembersWithVisitInfo` (starts ~line 1001) and `getJoinedMembers` (~line 982). Do NOT touch `TeamDao.kt` — no new queries are needed; `getByTeamIdAndDocType` already exists. Do NOT touch `getJoinedMembers`'s other callers.

steps:
1. In `getJoinedMembersWithVisitInfo`, call `teamDao.getByTeamIdAndDocType(teamId, "membership")` once and keep the result in a local `membershipRows`.
2. Extract the member-hydration body of `getJoinedMembers` into a private `getJoinedMembers(membershipRows)` overload (or inline it) so the public `getJoinedMembers(teamId)` signature and behavior stay identical for its other callers.
3. Derive `teamUserIds` from `membershipRows.mapNotNull { it.userId }` instead of `teamDao.getAllByTeamId(teamId)` (the admin-check only needs membership userIds; membership rows already carry them).
4. Derive `leaderIds` from `membershipRows.filter { it.isLeader }` instead of the second `getByTeamIdAndDocType` call.

acceptance: `./gradlew testDefaultDebugUnitTest` green; team members screen shows members and valid community-leader admins in leader-first order, identical to before.

size budget: ~35 changed lines, 1 file.

out of scope: no DAO/interface signature changes, no changes to visit-info or join-request logic.

---

### 2. hoist shared reads out of upload serializer lambdas (roadmap 5+7)

context: `UploadConfigs.kt` serializers run per uploaded item, so SharedPreferences and repository lookups fire per row. `customDeviceName` is a getter doing `sharedPrefManager.getCustomDeviceName()` read inside serializers at lines 56, 111, 212, 295 (and `androidId` at 111); `sharedPrefManager.getPlanetCode()`/`getParentCode()` sit inside the `Submissions` serializer at line 258; `userRepository.getUserById(...)` hits Room per item at lines 83, 242, 257 — the same `userId` recurs across items in a batch.

files: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` — `NewsActivities`, `SearchActivity` (~line 111), `ApkLog` (~line 212), `Rating` (~line 295), `TeamTask` (~line 83), `ExamResults` (~line 242), `Submissions` (~line 257). Do NOT touch the `UploadSerializer` types or the config plumbing in `UploadConfig.kt`.

steps:
1. Wrap each serializer's lambda with a per-run snapshot: read `customDeviceName`, `androidId`, `getPlanetCode()`, `getParentCode()` once into local `val`s captured by the lambda (they are constant for the whole upload run).
2. For `TeamTask`, `ExamResults` and `Submissions`, memoize `userRepository.getUserById(id)` in a `mutableMapOf<String?, UserEntity?>` keyed on userId inside the serializer scope so a repeated assignee/userId costs one Room read per run.
3. Keep null/empty handling (`task.assignee ?: ""`, `submission.userId?.let { }`) semantically identical.
4. Remove any now-unneeded per-item `val user =` recomputation duplicates.

acceptance: `./gradlew testDefaultDebugUnitTest` green; uploads still serialize identical payloads (same device name, planet codes, user objects).

size budget: ~45 changed lines, 1 file.

out of scope: no changes to upload endpoints, batching (`BATCH_SIZE`), or `UploadCoordinator`; no new DAO methods.

---

### 3. tighten the submissions read path: LIMIT 1 exam lookup and allocation-free flow dedup (roadmap 1+7)

context: two small wastes in the submissions path. `ExamDao.getByStepIdAndType` (`ExamDao.kt:38`) returns `List<StepExam>` but `SubmissionsRepositoryImpl.kt:401` only needs `.firstOrNull()`. Separately, `getSubmissionsFlow` (`SubmissionsRepositoryImpl.kt` ~line 81) does `old.zip(new).all { (o, n) -> ... }` inside `distinctUntilChanged`, allocating a `List<Pair>` on every emission.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt` and `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`isStepCompleted` ~line 401, `getSubmissionsFlow` ~line 81). Do NOT touch `CoursesRepositoryImpl.kt` — it legitimately uses both full lists at lines 543-544.

steps:
1. Add `getFirstByStepIdAndType(stepId: String, type: String): StepExam?` to `ExamDao` with `SELECT * FROM exams WHERE stepId = :stepId AND type = :type LIMIT 1` (mirror the `getByTypeAndName` LIMIT 1 style already at `ExamDao.kt:43`).
2. In `SubmissionsRepositoryImpl.isStepCompleted`, replace `examDao.getByStepIdAndType(stepId, "surveys").firstOrNull()` with `examDao.getFirstByStepIdAndType(stepId, "surveys")`.
3. In `getSubmissionsFlow`, replace the `zip` comparison with an indexed `for` loop comparing `id` and `lastUpdateTime` pairwise, same semantics.
4. Leave the list-returning `getByStepIdAndType` in place for `CoursesRepositoryImpl`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; step-completion checks and the submissions list behave identically.

size budget: ~25 changed lines, 2 files.

out of scope: no change to `getCourseStepData`, no Room schema changes, no new entities.

---

### 4. cache `UrlUtils.hostUrl` like `baseUrl`/`header` (roadmap 7)

context: `UrlUtils.hostUrl` (lines 83-108) reads four SharedPreferences values and runs `alternativeUrl.toUri()` on every access, while sibling getters `baseUrl` and `header` in the same file already cache behind a generation check. `ChatApiService` reads `hostUrl` once or twice per chat request (`ChatApiService.kt` lines 25-26 and ~48-49) and once more in its error log path, so every AI interaction re-reads prefs.

files: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` — `hostUrl` getter only. Do NOT touch `baseUrl`, `header`, `getUrl`, `dbUrl`, `spm`, or any caller including `ChatApiService.kt`.

steps:
1. Introduce a private cached value plus the four inputs it was computed from (scheme, hostIp, isAlternativeUrl, processedAlternativeUrl) — mirror the existing generation-check pattern used by `baseUrl` in this file.
2. On each `hostUrl` get, recompute only when any of the four inputs differ from the cached tuple.
3. Keep the `".org"`/`".gt"` suffix logic and the `try/catch` fallback warning identical.
4. If the file already holds a private generation field for `baseUrl`, reuse the same invalidation trigger so `hostUrl` refreshes whenever prefs change.

acceptance: `./gradlew testDefaultDebugUnitTest` green; chat still reaches the server after a URL switch (alternative URL toggle honored the next call after prefs change).

size budget: ~30 changed lines, 1 file.

out of scope: no API changes to `UrlUtils`, no caller edits, no persistence changes.

---

### 5. hoist the constant `"Other"` choice parse and per-button inflater in `ExamTakingFragment` (roadmap 7)

context: `ExamTakingFragment` parses the identical JSON literal `"""{"text":"Other","id":"other"}"""` through Gson every time a question renders an "Other" option — once in `showCheckBoxes` (~line 579) and once in `selectQuestion` (~line 599). `addRadioButton` (~line 608) also calls `LayoutInflater.from(activity)` for every radio button it inflates.

files: `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt` — `showCheckBoxes`, `selectQuestion`, `addRadioButton`. Do NOT touch `addCompoundButton`, `BaseExamFragment.kt`, or the answer-restoration block (~line 815+) that parses per-answer choice JSON — that data is per-answer and must stay dynamic.

steps:
1. Add a private lazy/val holding the parsed `JsonObject` for the `"Other"` choice (a fresh copy or `.deepCopy()` if Gson mutation semantics require it — prefer constructing one immutable instance and passing it read-only to `addCompoundButton`).
2. Replace both `GsonUtils.gson.fromJson(...)` calls with that field.
3. Hoist `LayoutInflater.from(activity)` in `addRadioButton` to a fragment-level lazy inflater.
4. Remove no longer-needed local variables.

acceptance: `./gradlew testDefaultDebugUnitTest` green; exams and surveys still render the "Other" option as a checkbox or radio matching `question.hasOtherOption`.

size budget: ~15 changed lines, 1 file.

out of scope: no UI redesign, no changes to answer submission/serialization.

---

### 6. index `leadersList` by name in `VoicesAdapter` (roadmap 7)

context: `VoicesAdapter.getCurrentLeader` (~lines 590-599) linearly scans `leadersList` comparing `leader.name == news.userName` on every bind of a news row where `userModel == null`. On the voices feed this runs once per bound row: O(rows × leaders).

files: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt` — constructor field `leadersList` (~line 72) and `getCurrentLeader`. Do NOT touch `leadersList` construction in the caller, `VoicesLabelManager`, or any other adapter method.

steps:
1. Add a private `leadersByName: Map<String, UserEntity>` built lazily once from `leadersList` (`associateBy { it.name }` with `it.name` nulls excluded or keyed safely — `UserEntity.name` is nullable; filter or use `filterNotNull`+`associate`).
2. Rewrite `getCurrentLeader` to `leadersByName[news.userName]` preserving the `userModel == null` guard and the `null` return.
3. If `leadersList` is ever reassigned post-construction, reset the map at that assignment site; otherwise a `by lazy` field suffices.

acceptance: `./gradlew testDefaultDebugUnitTest` green; voices feed still shows the leader context for community-authored posts.

size budget: ~15 changed lines, 1 file.

out of scope: no changes to news payload logic, markdown caches, or chip rendering.

---

### 7. cache formatted dates in the two submission list adapters (roadmap 7)

context: `SubmissionsAdapter.bind` (~line 78) calls `getFormattedDate(submission.startTime)` and `SubmissionsListAdapter` (~line 49) calls `TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)` on every bind. Sibling adapters (`TeamsAdapter`, `NotificationsAdapter`, `HealthUsersAdapter`, `FeedbackAdapter`, `PersonalsAdapter`) already keep a `dateCache` `HashMap<Long, String>`; these two do not.

files: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt` and `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt`. Do NOT touch `TimeUtils.kt` — the formatter cache there is orthogonal and stays.

steps:
1. Add `private val dateCache = HashMap<Long, String>()` to each adapter (copy the `HealthUsersAdapter`/`TeamsAdapter` idiom).
2. Wrap each call site: `dateCache.getOrPut(submission.startTime) { getFormattedDate(submission.startTime) }` and `dateCache.getOrPut(submission.lastUpdateTime) { TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime) }`.
3. No other changes to bind logic.

acceptance: `./gradlew testDefaultDebugUnitTest` green; submission rows show the same formatted dates.

size budget: ~8 changed lines, 2 files.

out of scope: no DiffUtil/payload changes, no layout changes.

---

### 8. resolve `FeedbackAdapter` label strings once instead of per bind (roadmap 7)

context: `FeedbackAdapter.onBindViewHolder` calls `localizedLabel(context, feedback.type)` and `localizedLabel(context, feedback.priority)` per bind; `localizedLabel` does `context.getString(resId)` for one of only five fixed `LABELS` entries. The adapter already caches `statusText`/`priorityText`/`openDateText` in `onCreateViewHolder` — the label strings should join that cache.

files: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt` — `localizedLabel` and `onCreateViewHolder`. Do NOT touch the `LABELS` map keys or the content-description format string.

steps:
1. In `onCreateViewHolder` (where `statusText` etc. are resolved), populate a `Map<Int, String>` (or `Map<String, String>` keyed on the lowercase label) resolving every `LABELS` value to its string.
2. Rewrite `localizedLabel` to consult the pre-resolved map first, falling back to the raw `value` when absent.
3. Keep `value?.lowercase(Locale.ROOT)` normalization semantics identical.
4. Remove the per-call `context.getString` from `localizedLabel` (the map is built once per adapter use).

acceptance: `./gradlew testDefaultDebugUnitTest` green; feedback rows still show localized type/priority/status.

size budget: ~20 changed lines, 1 file.

out of scope: no new strings, no layout changes.

---

### 9. memoize `formatLabelValue` in `VoicesLabelManager` (roadmap 7)

context: `VoicesLabelManager.showChips` calls `getLabel(label)` per chip per re-render, and `formatLabelValue` runs two regex passes plus a `split`/`titlecase`/`joinToString` chain for a finite label set (`Constants.LABELS`). `renderedStateCache` skips re-render when nothing changed, but every label add/remove re-formats all chips.

files: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt` — `getLabel`/`formatLabelValue` (~lines 117-143) and `showChips` (~line 64). Do NOT touch `RenderedState`, `updateAddLabelVisibility`, or `Constants.LABELS`.

steps:
1. Add a small `private val labelCache = mutableMapOf<String, String>()` (instance or companion — match file conventions) in `getLabel` scope.
2. Return `labelCache.getOrPut(raw) { formatLabelValue(raw) }` for the `Constants.LABEL_VALUE_TO_NAME`-miss path; constant lookups stay as-is.
3. Guard nothing else — labels are stable strings, so an unbounded map is fine (finite `Constants.LABELS` + user labels still bounded by data size).

acceptance: `./gradlew testDefaultDebugUnitTest` green; voice chips render the same display labels, including hyphen/underscore normalization.

size budget: ~12 changed lines, 1 file.

out of scope: no chip rendering/close-icon behavior changes, no new public API.

---

### 10. collapse the duplicated task→team-name batch helpers in `NotificationsRepositoryImpl` (roadmap 1+8)

context: `getTaskTeamNamesByTaskIds` (~lines 256-277) and `getTaskTeamNamesByTaskTitles` (~lines 312-338) are the same routine modulo the task fetch (`getTasksByIds` vs `getTasksByTitles`) and the map key (`task.id` vs `task.title`). Maintaining two copies invites drift on one of the most-touched repository files.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — the two private helpers and their call sites only. Do NOT touch `getJoinRequestDetailsBatch`, `teamsRepository` accessor, or notification DAO queries.

steps:
1. Extract a private `getTaskTeamNames(tasks: List<TeamTask>, keySelector: (TeamTask) -> String?)` (use the actual task model type used in the file) that does teamIds collection → `getTeamNamesByIds` → map build exactly once.
2. Re-implement `getTaskTeamNamesByTaskIds` as `getTaskTeamNames(teamsRepository.get().getTasksByIds(taskIds)) { it.id }`.
3. Re-implement `getTaskTeamNamesByTaskTitles` as `getTaskTeamNames(teamsRepository.get().getTasksByTitles(taskTitles)) { it.title }`.
4. Keep the empty-input early returns and `LinkedHashSet`/`teamId.isNotEmpty()` filters identical.

acceptance: `./gradlew testDefaultDebugUnitTest` green; notification titles still resolve to the right team names for both id- and title-keyed lookups.

size budget: ~30 changed lines (net negative), 1 file.

out of scope: no DAO changes, no notification-payload changes, no dedup beyond these two helpers.

---

signed: devin · devin (cognition ai) · normal effort
