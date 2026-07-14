# Refactor Roadmap — 10 Low-Conflict, Reviewable Tasks

Each task below lives in a **distinct set of files** so they can be reviewed and merged
in the same ~10 PR round without stepping on each other. Focus of this round:
reinforcing repository boundaries, killing cross-feature/data-source leaks, tightening
repository interfaces, and moving stray data functions from UI into repositories — plus a
few `DiffUtils.itemCallback` / `DiffUtils.calculateDiff` clean-ups. No use cases, no Compose,
no new unused code.

### Tighten LocalResourceRequest so the UI stops constructing RealmList

The `LocalResourceRequest` DTO exposes `io.realm.RealmList<String>` on the repository
boundary, which forces `AddResourceActivity` to import and build Realm collections in the UI
layer. Swapping these three fields to plain `List<String>` and converting to `RealmList`
inside the impl removes a Realm dependency from the UI and touches only the resources feature.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L15-L34"}

:::task-stub{title="Change LocalResourceRequest RealmList fields to List<String>"}
1. In `ResourcesRepository.kt`, change `subjects`, `levels`, and `resourceFor` on `LocalResourceRequest` from `io.realm.RealmList<String>?` to `List<String>?` and drop the `io.realm` import.
2. In `ResourcesRepositoryImpl`, at the point the request is written to Realm, wrap each list into a `RealmList` (e.g. `RealmList<String>().apply { addAll(list) }`) before persisting.
3. In `AddResourceActivity`, build plain `ArrayList<String>()`/`List<String>` for the three fields and remove the now-unused `io.realm.RealmList` import.
4. Build the resources flow and confirm adding a local resource still saves subjects/levels/resourceFor.
:::

### Move meetup entity assembly out of TeamCalendarViewModel into EventsRepository

`TeamCalendarViewModel.createMeetup` builds a full `RealmMeetup` and hand-serializes the
`sync`/`link` JSON with Gson inside the ViewModel — data-shaping that belongs behind the
repository. Passing the params straight to `EventsRepository` lets the ViewModel drop its
`Gson`/`JsonObject`/`RealmMeetup` construction and keeps persistence logic in one place.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt#L56-L88"}

:::task-stub{title="Move RealmMeetup/sync-link assembly into EventsRepository.createMeetup"}
1. Add `suspend fun createMeetup(params: MeetupCreationParams): Boolean` to `EventsRepository` (reuse the existing params type, moving it beside the interface if needed).
2. In `EventsRepositoryImpl`, build the `RealmMeetup` and the `sync`/`link` JSON strings that currently live in the ViewModel, then persist via `DatabaseService`/`RealmRepository`.
3. In `TeamCalendarViewModel.createMeetup`, replace the entity/JSON building with a single call to `eventsRepository.createMeetup(params)` and remove the `Gson`/`JsonObject` imports if now unused.
4. Verify creating a team meetup still stores the correct `sync` and `link` payloads.
:::

### Give FeedbackRepository.addReply a typed signature instead of a UI-built JsonObject

`FeedbackDetailActivity` assembles the reply `JsonObject` (message/time/user) and passes it
through the ViewModel into `FeedbackRepository.addReply(id, JsonObject)`. Moving the JSON
construction into the impl and exposing a typed method removes Gson from the feedback UI and
tightens the interface — all within the feedback feature.

:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt#L101-L106"}

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L21-L21"}

:::task-stub{title="Replace FeedbackRepository.addReply(JsonObject) with typed params"}
1. Change the interface method to `suspend fun addReply(id: String?, message: String, user: String)`.
2. In `FeedbackRepositoryImpl`, build the `{message, time, user}` JSON (using the current `Date().time` value) internally before persisting.
3. Update `FeedbackDetailViewModel.addReply` to forward `message`/`user` instead of a `JsonObject`.
4. In `FeedbackDetailActivity`, pass the raw strings and delete the local `JsonObject` construction and its `com.google.gson` import if unused.
5. Send a feedback reply and confirm it is stored and rendered.
:::

### Move Dictionary Realm access out of DictionaryActivity into a repository

`DictionaryActivity` is one of the last screens still calling `DatabaseService`/raw Realm
(`executeTransactionAsync`, `realm.where(...)`, `copyFromRealm`) directly from the UI. Extracting
these three operations into a small `DictionaryRepository` built on `RealmRepository` completes
the data-layer boundary for this feature.

:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L121-L160"}

:::task-stub{title="Introduce DictionaryRepository and remove raw Realm from DictionaryActivity"}
1. Create `DictionaryRepository` + `DictionaryRepositoryImpl(databaseService, realmDispatcher)` extending `RealmRepository`.
2. Add three suspend methods: bulk-insert from a `JsonArray`, `count(): Long`, and `findByWord(query: String): RealmDictionary?` (returning a detached copy).
3. Bind the interface in `RepositoryModule`.
4. Inject `DictionaryRepository` into `DictionaryActivity` and replace every `databaseService`/`realm.where` call with repository calls; drop the `DatabaseService` and Realm imports.
5. Verify dictionary import, count display, and word lookup still work.
:::

### Replace UserRepository.createMember(JsonObject) with the existing typed MemberInfo

`BecomeMemberActivity.buildMemberJson` assembles the entire new-user document in the UI and
hands a `JsonObject` to `userRepository.createMember`. A typed `MemberInfo` already exists, so
the JSON building can move into the impl, removing member-serialization from the activity.

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=142 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L123-L142"}

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L73-L73"}

:::task-stub{title="Make UserRepository.createMember take MemberInfo instead of JsonObject"}
1. Change the interface to `suspend fun createMember(info: MemberInfo): Pair<Boolean, String>` (move `MemberInfo` to a shared location if it currently lives in the activity).
2. In `UserRepositoryImpl`, port the `buildMemberJson` field mapping into the impl (including `planetCode`/`parentCode`/androidId values it needs — pass those in via `MemberInfo` rather than reading UI state).
3. In `BecomeMemberActivity`, call `userRepository.createMember(info)` and delete `buildMemberJson` plus the `com.google.gson` import if unused.
4. Register a new member and confirm the persisted document is unchanged.
:::

### Give TeamsRepository typed finance-report methods instead of JsonObject payloads

`EnterprisesViewModel.addReport` and `updateReport` each build a finance `JsonObject` inline
and call `teamsRepository.addReport(JsonObject)` / `updateReport(id, JsonObject)`. Accepting a
typed report params object and building the JSON in the impl tightens the interface and removes
Gson from the enterprises ViewModel.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt#L52-L63"}

:codex-file-citation[codex-file-citation]{line_range_start=115 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L115-L117"}

:::task-stub{title="Add typed addReport/updateReport params to TeamsRepository"}
1. Define a `FinanceReportParams` data class (description, balances, dates) next to `TeamsRepository`.
2. Change `addReport`/`updateReport` to accept the params type (plus `reportId` for update) instead of `JsonObject`.
3. In `TeamsRepositoryImpl`, build the report `JsonObject` (including `createdDate`/`updatedDate`/`updated` handling) internally.
4. Update `EnterprisesViewModel.addReport`/`updateReport` to pass the params object and remove the local `JsonObject` construction.
5. Verify creating and editing an enterprise finance report still persists the same fields.
:::

### Move health-examination condition parsing into HealthRepository

`HealthExaminationActivity` parses the stored `examination.conditions` JSON string with Gson in
two places to drive its chip/checkbox UI. Exposing a `getExaminationConditions(...):
Map<String, Boolean>` on `HealthRepository` moves this data-source parsing behind the boundary
and de-duplicates the two call sites.

:codex-file-citation[codex-file-citation]{line_range_start=201 line_range_end=224 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt#L201-L224"}

:::task-stub{title="Expose parsed examination conditions from HealthRepository"}
1. Add `suspend fun getExaminationConditions(examination: RealmHealthExamination?): Map<String, Boolean>` to `HealthRepository`.
2. In `HealthRepositoryImpl`, parse the `conditions` JSON string into the map (reusing the existing boolean-extraction logic).
3. In `HealthExaminationActivity`, inject `HealthRepository`, fetch the map once, and use it in both `preloadCustomDiagnosis` and `showCheckbox` instead of calling `JsonUtils.gson.fromJson`.
4. Remove the now-unused `JsonObject`/`JsonUtils` imports if applicable.
5. Verify examination chips and checkboxes still reflect saved conditions.
:::

### Use DiffUtils.calculateDiff in TeamPagerAdapter

`TeamPagerAdapter.updatePages` hand-rolls an anonymous `DiffUtil.Callback`. The project already
has a `DiffUtils.calculateDiff` helper that expresses the exact same old/new list comparison,
so this collapses to a small, self-contained call in one file.

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt#L31-L42"}

:::task-stub{title="Replace TeamPagerAdapter manual DiffUtil.Callback with DiffUtils.calculateDiff"}
1. In `updatePages`, call `DiffUtils.calculateDiff(pages, newPages, areItemsTheSame = { a, b -> a.id == b.id }, areContentsTheSame = { a, b -> a == b })`.
2. Delete the anonymous `object : DiffUtil.Callback()` block and the direct `DiffUtil` import if unused.
3. Keep `pages = newPages.toList()` and `diffResult.dispatchUpdatesTo(this)`.
4. Verify team tab pages still update without visual glitches.
:::

### Use DiffUtils.calculateDiff in CoursesPagerAdapter

`CoursesPagerAdapter.submitList` also defines its own `DiffUtil.Callback` for the step list.
Routing the step comparison through `DiffUtils.calculateDiff` keeps the header/offset handling
but removes the boilerplate callback, in a single isolated file.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt#L18-L36"}

:::task-stub{title="Route CoursesPagerAdapter step diffing through DiffUtils.calculateDiff"}
1. Build header-prefixed lists (e.g. prepend a sentinel for the detail fragment) for old and new steps so index 0 stays the course-detail page.
2. Call `DiffUtils.calculateDiff(oldWithHeader, newWithHeader, areItemsTheSame = { a, b -> a == b }, areContentsTheSame = { a, b -> a == b })`.
3. Remove the anonymous `object : DiffUtil.Callback()` block; keep the `itemIds` bookkeeping and `dispatchUpdatesTo`.
4. Verify swiping course steps still works after a `submitList` update.
:::

### Use DiffUtils.itemCallback in ChatShareTargetAdapter

`ChatShareTargetAdapter` is already a `ListAdapter` but declares its `DiffUtil.ItemCallback`
as an anonymous object. Replacing it with the shared `DiffUtils.itemCallback` factory keeps the
adapter consistent with the rest of the codebase and is a one-file change.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=81 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-roadmap-tasks-aaaro5/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L73-L81"}

:::task-stub{title="Convert ChatShareTargetAdapter DiffCallback to DiffUtils.itemCallback"}
1. Replace the `object : DiffUtil.ItemCallback<ChatShareTargetItem>()` with `DiffUtils.itemCallback(areItemsTheSame = { a, b -> a.title == b.title && a.isGroup == b.isGroup }, areContentsTheSame = { a, b -> a == b })`.
2. Remove the direct `androidx.recyclerview.widget.DiffUtil` import if no longer referenced.
3. Confirm the chat share-target list still diffs and renders correctly.
:::

### Testing

- For each task run the default-flavor unit tests (`./gradlew testDefaultDebugUnitTest`) — this is what CI enforces — and add/adjust the repository test next to the existing ones when a repository method signature changes (tasks 1–8).
- Build the affected flavor (`./gradlew assembleDefaultDebug`) and manually exercise the one screen each task touches; the tasks are file-disjoint, so they can be verified and merged independently within the same review round.
