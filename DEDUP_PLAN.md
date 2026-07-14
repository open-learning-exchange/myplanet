# Deduplicated Refactor Plan — 60 subtasks → 23 findings

_Six queued task groups (10 subtasks each) collapse into 23 distinct findings. The dominant duplication is **DictionaryRepository** (6 copies) and the **DiffUtils** migrations (11 copies across several adapters). Verification against the current branch shows most `DiffUtils.itemCallback` migrations are **already complete**, one subtask rests on a **false premise**, and the genuine remaining work is a small set of repository/ViewModel boundary extractions._

### Extract dictionary persistence into a DictionaryRepository

Six subtasks (T1.1, T2.1, T3.1, T4.1, T5.4, T6.1) all ask for the same thing: `DictionaryActivity` still injects `DatabaseService` and runs `executeTransactionAsync`/`realm.where` directly, the last raw-Realm holdout per CLAUDE.md. Merge into one repository extraction: count, one-time JSON import, and exact-word lookup returning a detached copy.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L38-L38"}
:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=147 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L121-L147"}

:::task-stub{title="Extract DictionaryActivity Realm access into DictionaryRepository"}
1. Create `DictionaryRepository` + `DictionaryRepositoryImpl(databaseService, dispatcherProvider)` extending `RealmRepository`.
2. Add suspend methods: `count(): Long`, `importFromJsonArray(array)`, and `findByWord(query): RealmDictionary?` returning a detached copy.
3. Bind the interface in `RepositoryModule`.
4. Inject `DictionaryRepository` into `DictionaryActivity`; replace every `databaseService`/`realm.where` call and remove the `DatabaseService`, `io.realm.Case`, and `RealmDictionary` Realm imports.
5. Keep file download/read and UI rendering in the activity; verify import, count display, and word lookup still work.
:::

### Convert ChatShareTargetAdapter to DiffUtils.itemCallback

Five subtasks (T1.2, T2.3, T3.8, T4.4, T5.10) target the one adapter still using a raw `object : DiffUtil.ItemCallback`. Every other `ListAdapter` in the app already uses the shared helper, so this is the only real DiffUtils-itemCallback migration left.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L10-L10"}
:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L73-L73"}

:::task-stub{title="Replace ChatShareTargetAdapter custom DiffUtil.ItemCallback with DiffUtils.itemCallback"}
1. Replace `object : DiffUtil.ItemCallback<ChatShareTargetItem>()` with `DiffUtils.itemCallback(areItemsTheSame = { a, b -> a.title == b.title && a.isGroup == b.isGroup }, areContentsTheSame = { a, b -> a == b })`.
2. Add `import org.ole.planet.myplanet.utils.DiffUtils` and remove the `androidx.recyclerview.widget.DiffUtil` import if unreferenced.
3. Leave view-holder code unchanged; confirm the share-target list still diffs/renders.
:::

### Route the two pager adapters through DiffUtils.calculateDiff

Four subtasks (T4.5, T4.6, T5.8, T5.9) cover `CoursesPagerAdapter` and `TeamPagerAdapter`, the only adapters still hand-rolling `object : DiffUtil.Callback()` + `DiffUtil.calculateDiff`. Merge into one finding using the shared `DiffUtils.calculateDiff` helper for both.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt#L19-L36"}
:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt#L32-L42"}

:::task-stub{title="Migrate CoursesPagerAdapter and TeamPagerAdapter to DiffUtils.calculateDiff"}
1. In `TeamPagerAdapter.updatePages`, replace the anonymous `DiffUtil.Callback` with `DiffUtils.calculateDiff(pages, newPages, areItemsTheSame = { a, b -> a.id == b.id }, areContentsTheSame = { a, b -> a == b })`; keep `pages = newPages.toList()` and `dispatchUpdatesTo(this)`.
2. In `CoursesPagerAdapter`, build header-prefixed old/new step lists (sentinel at index 0 for the detail page) and call `DiffUtils.calculateDiff(oldWithHeader, newWithHeader, { a, b -> a == b }, { a, b -> a == b })`; keep `itemIds` bookkeeping.
3. Remove the direct `androidx.recyclerview.widget.DiffUtil` imports if unused; verify tab/step swiping still updates without glitches.
:::

### Move two inner adapters into their own files (DiffUtils already applied)

T1.3 (FeedbackReplyAdapter) and T1.8 (StatsAdapter) each conflate two asks; the DiffUtils half is already done (both use `DiffUtils.itemCallback`), so only the file-extraction remains. `FeedbackReplyAdapter` is a nested `inner class` in `FeedbackDetailActivity` and `StatsAdapter` is a nested `inner class` in `UserProfileFragment`.

:codex-file-citation[codex-file-citation]{line_range_start=141 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt#L141-L160"}
:codex-file-citation[codex-file-citation]{line_range_start=471 line_range_end=472 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L471-L472"}

:::task-stub{title="Extract FeedbackReplyAdapter and StatsAdapter into standalone files"}
1. Move `FeedbackReplyAdapter` (and its `ReplyViewHolder`) out of `FeedbackDetailActivity` into `FeedbackReplyAdapter.kt`; keep the existing `DiffUtils.itemCallback` `DIFF_CALLBACK`.
2. Move `StatsAdapter` out of `UserProfileFragment` into `StatsAdapter.kt`, keeping its `DiffUtils.itemCallback<Pair<String, String?>>`.
3. Update imports/usages in the two host files; no behavior change expected.
:::

### Close already-completed DiffUtils migrations as no-ops

Eight subtasks (T1.4 Personals, T1.5 QuestionAnswer, T1.6 Achievements, T1.7-diff HealthExamination, T1.9 UserArray, T1.10 Users, T2.4 Teams, T6.10-diff HealthUsers) ask to migrate adapters that **already** use `DiffUtils.itemCallback`. No code change is required; verify and close.

:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt#L91-L92"}
:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/QuestionAnswerAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/QuestionAnswerAdapter.kt#L55-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementsAdapter.kt#L55-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=142 line_range_end=142 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt#L142-L142"}
:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=143 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt#L143-L143"}
:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt#L17-L17"}

:::task-stub{title="Verify already-migrated DiffUtils adapters and close subtasks"}
1. Confirm PersonalsAdapter, QuestionAnswerAdapter, AchievementsAdapter, HealthExaminationAdapter, UserArrayAdapter, UsersAdapter, TeamsAdapter, and HealthUsersAdapter all use `DiffUtils.itemCallback`.
2. Run `./gradlew testDefaultDebugUnitTest --tests "*AdapterTest"` to confirm existing diff tests still pass.
3. Mark the eight subtasks complete — no source change needed.
:::

### Reject the DashboardPluginFragment "unused import" subtask (false premise)

T2.5 asks to remove `import io.realm.RealmObject` claiming it is unused, but `RealmObject` is the parameter type of `setTextViewProperties` and `getLayout`. Removing it breaks compilation. The salvageable intent (T6.7) is a genuine UI-model decoupling, tracked under the UI-model finding.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt#L12-L12"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=128 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt#L101-L128"}

:::task-stub{title="Reject invalid RealmObject import removal; track real decoupling separately"}
1. Do not remove `import io.realm.RealmObject` — it is used at lines 101 and 128.
2. Close T2.5 as invalid.
3. If decoupling is desired (T6.7), introduce a UI data class in `DashboardViewModel` and change `setTextViewProperties`/`getLayout` signatures — handle under the UI-model finding, not as an import cleanup.
:::

### Stop VoicesLabelManager from mutating Realm objects directly

T2.2 and T2.8 are the same concern: after invoking the repository callbacks, `VoicesLabelManager` also mutates the live `voice.labels` list in place. The repository (`addLabelFn`/`removeLabelFn`) already owns persistence, so the direct mutation is redundant and touches a detached/live object from the UI.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L49-L54"}
:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L91-L93"}

:::task-stub{title="Remove direct voice.labels mutation from VoicesLabelManager"}
1. Delete `voice.labels?.add(selectedLabel)` (line 54) and `voice.labels?.remove(selectedLabel)` (line 93).
2. Rely solely on `addLabelFn`/`removeLabelFn`, which call `VoicesRepository.addLabel`/`removeLabel`.
3. If the UI needs an immediate refresh, emit a state/refresh callback instead of mutating the model; verify chips update after add/remove.
:::

### Replace JsonObject payloads at repository boundaries with typed params

Four Task-5 subtasks (T5.2 meetup, T5.3 feedback reply, T5.5 create member, T5.6 finance report) share one pattern: the ViewModel/Activity builds a `JsonObject`/`RealmMeetup` and hands it to the repository. Move the assembly into the repository behind typed params so UI code stops constructing persistence payloads.

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L21-L21"}
:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L73-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=115 line_range_end=117 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L115-L117"}
:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt#L39-L47"}

:::task-stub{title="Introduce typed params for four repository write APIs"}
1. `FeedbackRepository.addReply(id, message, user)` — build the `{message,time,user}` JSON inside the impl; update `FeedbackDetailViewModel`/`FeedbackDetailActivity` to pass strings.
2. `UserRepository.createMember(info: MemberInfo)` — port `buildMemberJson` into the impl; `BecomeMemberActivity` passes the typed object.
3. `TeamsRepository.addReport(FinanceReportParams)` / `updateReport(reportId, params)` — build the report JSON internally; update `EnterprisesViewModel`.
4. `EventsRepository.createMeetup(params)` — move `RealmMeetup` + `sync`/`link` JSON assembly out of `TeamCalendarViewModel`, dropping its `Gson`/`JsonObject` imports.
5. Verify each persisted document is byte-for-byte unchanged.
:::

### Change LocalResourceRequest RealmList fields to List<String>

T5.1 and T6.6 both target `LocalResourceRequest`, whose `subjects`/`levels`/`resourceFor` are typed as `io.realm.RealmList`, leaking Realm into the request DTO that `AddResourceActivity` populates. Convert to `List<String>` and wrap into `RealmList` only at the persistence point.

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L15-L29"}

:::task-stub{title="Decouple LocalResourceRequest from io.realm.RealmList"}
1. Change `subjects`, `levels`, `resourceFor` on `LocalResourceRequest` to `List<String>?` and drop the `io.realm` import.
2. In `ResourcesRepositoryImpl`, wrap each list into a `RealmList` before persisting.
3. In `AddResourceActivity`, build plain `List<String>` and remove the `io.realm.RealmList` import.
4. Verify adding a local resource still saves subjects/levels/resourceFor.
:::

### Expose parsed examination conditions from HealthRepository

T5.7: `HealthExaminationActivity` parses the `conditions` JSON string via `JsonUtils.gson.fromJson` in `preloadCustomDiagnosis`/`showCheckbox`. Move parsing behind a repository method returning a `Map<String, Boolean>`.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt#L37-L39"}

:::task-stub{title="Add HealthRepository.getExaminationConditions"}
1. Add `suspend fun getExaminationConditions(examination: RealmHealthExamination?): Map<String, Boolean>` to `HealthRepository`; implement the boolean-extraction parsing in the impl.
2. Inject `HealthRepository` into `HealthExaminationActivity`, fetch the map once, and use it in `preloadCustomDiagnosis` and `showCheckbox`.
3. Remove now-unused `JsonObject`/`JsonUtils` imports; verify chips/checkboxes reflect saved conditions.
:::

### Tie adapter preview coroutines to view-holder lifecycle

T6.8 (`InlineResourceAdapter`) and T6.9 (`VoicesAdapterHelper`) both pass a parent/fragment `CoroutineScope` into an adapter and launch work that outlives recycled view holders. Manage cancellation at the view-binding level instead.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=37 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L37-L37"}
:codex-file-citation[codex-file-citation]{line_range_start=181 line_range_end=181 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L181-L181"}
:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt#L13-L15"}

:::task-stub{title="Scope adapter coroutines to view holders instead of parent scope"}
1. Remove `parentScope` from `InlineResourceAdapter`'s constructor; store a per-holder `Job` and cancel it in `onViewRecycled`.
2. Make `VoicesAdapterHelper.createOnAnimateTyping` return a disposable/`Job` the ViewHolder cancels on recycle instead of capturing the caller's scope.
3. Verify preview loading and typing animation still run and stop cleanly on scroll.
:::

### Decouple list adapters from Realm models via UI items

Several subtasks (T6.2 HealthExamination, T6.3 Courses selection, T6.4 ChatHistory, T6.5 TeamsTasks, T3.9 health screen, T6.7 DashboardPlugin) push Realm models straight into adapters/callbacks. Introduce lightweight UI data classes mapped in the ViewModel so adapters and click listeners carry primitives/ids, not `RealmObject`s.

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=37 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L31-L37"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksAdapter.kt#L22-L99"}
:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L84-L86"}
:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt#L33-L33"}

:::task-stub{title="Introduce UI item models for Realm-backed adapters"}
1. `TeamsTasksAdapter`: pass `taskId: String` + completion state through `onCheckChange`/`onClickMore`; handle the update in `TeamViewModel`/`TeamsRepository`.
2. `CoursesFragment`: track selected course ids in the adapter/ViewModel and drop the `mapNotNull` back to `RealmMyCourse`.
3. `ChatHistoryAdapter`: consume a `ChatHistoryItem` mapped in `ChatViewModel`; pass ids to nested views.
4. `HealthExaminationAdapter` (+ health screen T3.9): map `RealmHealthExamination`/patient rows to UI items in the ViewModel, moving condition parsing out of the adapter.
5. Verify each list renders and updates identically.
:::

### Use DispatcherProvider for TeamViewModel repository collection

T4.8: `TeamViewModel.loadTasks`/`loadTeams` launch on `viewModelScope.launch` without the injected `dispatcherProvider`, unlike the project convention. Route through `dispatcherProvider.main` + `withContext(dispatcherProvider.io)`.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L43-L66"}

:::task-stub{title="Route TeamViewModel loads through DispatcherProvider"}
1. Wrap repository work in `loadTasks`/`loadTeams` with `dispatcherProvider.main` + `flowOn`/`withContext(dispatcherProvider.io)`.
2. Keep the existing `loadJob`/`loadTaskJob` cancellation behavior.
3. Do not change repository APIs or filtering logic.
:::

### Remove one Realm-model overload from FileUploader

T4.9: `FileUploader` exposes three `uploadAttachment` overloads keyed on Realm models (`RealmMyPersonal`, `RealmMyLibrary`, `RealmSubmitPhotos`). Add a primitive variant and migrate one overload (start with `RealmSubmitPhotos`).

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt#L32-L48"}

:::task-stub{title="Replace one FileUploader Realm-model overload with a primitive variant"}
1. Add `uploadAttachment(localPath, destinationFormat, id, rev, listener)`.
2. Move `RealmSubmitPhotos` field extraction to the caller/owning service.
3. Delete the `RealmSubmitPhotos` overload once all call sites use the primitive request; verify photo submission upload still works.
:::

### Move post-login upload/reachability orchestration behind a sync boundary

Four subtasks (T3.2 personals upload, T3.3 survey submission, T3.4 user-data upload, T3.5 post-login sync) all leave UI classes driving `UploadManager`/`UploadToShelfService`/`WorkManager` directly. Push reachability checks and upload kickoff behind repository/coordinator APIs that return simple UI state.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=89 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L35-L89"}
:codex-file-citation[codex-file-citation]{line_range_start=57 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L57-L199"}

:::task-stub{title="Hide upload/reachability orchestration behind repository/coordinator seams"}
1. Add `PersonalsViewModel.upload(...)` delegating to `PersonalsRepository`; drop `UploadManager` from `PersonalsFragment` and observe result state.
2. Add a submissions sync entry point owning reachability + upload kickoff; leave `UserInformationFragment` to collect input and react to success/failure.
3. Wrap the `ProcessUserDataActivity`/`SyncActivity` login + bulk-upload WorkManager entry points behind a sync/upload repository returning UI state.
4. Keep each change scoped to one fragment/activity + its seam to stay reviewable.
:::

### Consolidate server-selection and server-policy config

T3.6 (ServerDialogExtensions defaults) and T3.7 (shared server policy for Dashboard/WebView) both rebuild server-URL/PIN/trust logic at call sites. Centralize default protocol/PIN/community-URL resolution and the allowed/trusted-URL list in one configuration source.

:codex-file-citation[codex-file-citation]{line_range_start=83 line_range_end=113 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L83-L113"}
:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=113 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L113-L113"}

:::task-stub{title="Introduce a shared server-config/policy source"}
1. Move default protocol, default PIN, and selected-community URL resolution into one configuration-facing API; `ServerDialogExtensions` only binds values and dispatches actions.
2. Add a shared provider for allowed/trusted server URLs; update `DashboardActivity` (challenge checks) and `WebViewActivity` (trust) to consume it.
3. Keep the two PRs narrow to their call sites and the new shared object.
:::

### Move remaining SyncManager/TransactionSyncManager domain writes into repositories

T4.2, T4.3, and T2.9 converge: sync managers still hold `ApiInterface`/`DatabaseService` and do domain-specific parsing/writes. Most tables already delegate to `bulkInsert*` repository methods; migrate the stragglers one branch at a time without changing phase ordering.

:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L63-L63"}
:codex-file-citation[codex-file-citation]{line_range_start=255 line_range_end=258 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L255-L258"}

:::task-stub{title="Migrate one sync-manager domain write/helper into its repository"}
1. Pick one `SyncManager` private helper that takes `ApiInterface` and touches a single domain; add the smallest method to that domain repository and move the API call + parsing there, then drop the helper's `ApiInterface` param.
2. Pick one `TransactionSyncManager` branch that writes a single Realm model with a repo already injected; add a domain-typed repository method and remove that branch's `DatabaseService` usage.
3. Keep sync orchestration/phase ordering unchanged; verify a full sync still completes.
:::

### Move DashboardViewModel profile assembly into a repository

T4.7: `DashboardViewModel` assembles fullName fallback + offlineLogins inside its `profileJob`. Move the metadata assembly to a focused repository method so the ViewModel only launches/collects/updates `UiState`.

:codex-file-citation[codex-file-citation]{line_range_start=159 line_range_end=171 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L159-L171"}

:::task-stub{title="Extract dashboard profile metadata into a repository method"}
1. Add a repository method returning `fullName` + `offlineLogins` for a user id (on `UserRepository` if it can depend on `ActivitiesRepository` without a cycle).
2. Move the name-fallback/blank-name handling out of `DashboardViewModel`.
3. Leave course/team/library observers untouched in this change.
:::

### Push chat-history realtime refresh behind the ViewModel

T3.8 (repository half): `ChatHistoryFragment` calls the `RealtimeSyncManager` singleton directly to trigger refreshes. Expose the refresh trigger through `ChatViewModel`/a repository flow instead. (The adapter half of T3.8 is covered by the ChatShareTargetAdapter finding.)

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L59-L59"}
:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=158 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L155-L158"}

:::task-stub{title="Move chat realtime refresh trigger into ChatViewModel"}
1. Replace the fragment's `RealtimeSyncManager.getInstance()` usage with a ViewModel-owned refresh flow/trigger.
2. Have the fragment observe ViewModel state to call `refreshChatHistory`.
3. Keep the chat-history screen scoped separately from chat-detail work.
:::

### Replace BaseTeamFragment profileDbHandler with UserRepository

T2.6: `BaseTeamFragment` reads the current user via `profileDbHandler.getUserModel()` rather than the repository seam. Switch to `UserRepository.getUserModel()`.

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L47-L47"}

:::task-stub{title="Use UserRepository.getUserModel in BaseTeamFragment"}
1. Confirm `UserRepository` exposes `getUserModel` (add it if missing).
2. Inject `UserRepository` into `BaseTeamFragment`.
3. Replace `profileDbHandler.getUserModel()` with `userRepository.getUserModel()`.
:::

### Narrow sync-only injection sites to the split sync interfaces

T3.10 and T4.10 both target interface breadth: sync-only consumers inject the full `UserRepository`/`TeamsRepository`/`ResourcesRepository`, while narrow sync interfaces (e.g. `teamsSyncRepository`) already exist. Retarget the injections and shrink one caller-facing `ResourcesRepository` slice.

:codex-file-citation[codex-file-citation]{line_range_start=256 line_range_end=256 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L256-L256"}
:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L63-L65"}

:::task-stub{title="Point sync-only consumers at narrow sync interfaces"}
1. Audit injections; switch sync-only consumers from `UserRepository`/`TeamsRepository` to `UserSyncRepository`/`TeamsSyncRepository` where the narrow interface already exists.
2. Identify the first wide-interface methods that stop leaking once injections are narrowed.
3. Move/rename one narrow `ResourcesRepository` slice (e.g. course-resource grouping) behind a more explicit interface only if callers live in one feature package.
4. Keep the PR to DI + injection-site edits so it lands ahead of larger repository splits.
:::

### Documentation and audit-only subtasks

Three subtasks require no behavior change: T2.7 (KDoc on `queryListFlow`), T2.9-doc (document `bulkInsert*` responsibilities), and T2.10 (audit `FileUtils.getSDPathFromUrl` call sites). Batch them as a docs/audit pass.

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L66-L66"}
:codex-file-citation[codex-file-citation]{line_range_start=255 line_range_end=258 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L255-L258"}

:::task-stub{title="Documentation and audit pass"}
1. Add KDoc to `queryListFlow` covering Flow lifecycle (listener removal, Realm close) and `@throws`.
2. Enumerate `TransactionSyncManager` `bulkInsert*` calls, confirm each has a repository method, and add TODOs for any that should move.
3. List `FileUtils.getSDPathFromUrl` call sites (FileUtils, DictionaryActivity, DownloadService, DownloadWorker, ConfigurationsRepositoryImpl) and note which belong in `ResourcesRepository`.
:::

## Testing

- Run `./gradlew testDefaultDebugUnitTest` after each finding; it is CI-enforced and covers the repositories, sync managers, ViewModels, and the `DiffUtils`/adapter tests.
- For the DictionaryRepository, typed-param, and FileUploader findings, add MockK + `runTest` tests mirroring the `main` package path so CI picks them up.
- For adapter DiffUtils/UI-model changes, rely on the existing `DiffUtilsTest`/`*AdapterTest` suites and add cases for new item/content equality rules.
