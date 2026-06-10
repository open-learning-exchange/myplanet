### Move DictionaryActivity's direct Realm access behind a repository
DictionaryActivity queries Realm directly with `databaseService.withRealm` in lifecycle methods and in the search click handler, blocking the main thread and bypassing the repository layer every other screen uses. Extracting a small DictionaryRepository on top of RealmRepository removes main-thread queries and finishes the data-layer cleanup for this screen.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L107"}

:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L109-L135"}

:::task-stub{title="Extract DictionaryRepository and remove direct Realm queries from DictionaryActivity"}
1. Create `DictionaryRepository` interface with `suspend fun getDictionaryCount(): Long`, `suspend fun isDictionaryEmpty(): Boolean`, `suspend fun findWord(word: String): RealmDictionary?`, and `suspend fun insertDictionary(jsonArray: JsonArray)`.
2. Create `DictionaryRepositoryImpl` extending `RealmRepository` (constructor-injected `DatabaseService` + `@RealmDispatcher` dispatcher, same as `TagsRepositoryImpl`), moving the count query, the case-insensitive `word` lookup, and the bulk-insert transaction from the activity into it.
3. Bind the pair in `RepositoryModule`.
4. In `DictionaryActivity`, replace the `databaseService.withRealm` blocks in `loadDictionaryIfNeeded`, `loadDictionaryCount`, and `setClickListener` with repository calls launched from `lifecycleScope`; drop the `databaseService` field injection and the `io.realm.Case` import.
:::

### Push team filtering into Realm queries in VoicesRepositoryImpl
`getNewsByTeamId` loads every top-level news item, filters in Kotlin by parsing `viewIn` JSON per row, and calls `copyFromRealm` once per matching item; `getDiscussionsByTeamIdFlow` repeats the same in-memory filter on every emission. The same file already demonstrates the query-level alternative — `isAlreadyShared` matches the serialized `viewIn` JSON with a `contains` predicate — so the filter can move into the Realm query with a single bulk copy.

:codex-file-citation[codex-file-citation]{line_range_start=149 line_range_end=177 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L149-L177"}

:codex-file-citation[codex-file-citation]{line_range_start=119 line_range_end=126 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L119-L126"}

:codex-file-citation[codex-file-citation]{line_range_start=221 line_range_end=235 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L221-L235"}

:::task-stub{title="Replace in-memory team news filtering with Realm query predicates in VoicesRepositoryImpl"}
1. In `getNewsByTeamId`, extend the Realm query with `beginGroup().equalTo("viewableBy", "teams", Case.INSENSITIVE).equalTo("viewableId", teamId, Case.INSENSITIVE).or().contains("viewIn", "\"_id\":\"" + teamId + "\"", Case.INSENSITIVE).endGroup()`, mirroring the predicate style used by `isAlreadyShared`.
2. Replace the per-item `realm.copyFromRealm(news)` calls inside the loop with a single `realm.copyFromRealm(results)` on the query result, and delete the manual loop plus per-row Gson parsing.
3. Apply the same compound predicate inside the `queryListFlow` builder of `getDiscussionsByTeamIdFlow` and remove its `map { discussions.filter { ... } }` block.
4. Verify team discussion lists still show community-shared and team-scoped posts on the team detail screen.
:::

### Filter and sort courses inside the Realm query in filterCourses
`filterCourses` runs the Realm query, then drops blank titles and sorts the whole result with Kotlin `sortedWith` before copying every object out. Moving the emptiness check and title sort into the query lets Realm do the work lazily and shrinks the copied set; only the `@Transient` `isMyCourse` partition has to stay in memory.

:codex-file-citation[codex-file-citation]{line_range_start=307 line_range_end=327 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L307-L327"}

:::task-stub{title="Move blank-title filter and title sort into the Realm query in CoursesRepositoryImpl.filterCourses"}
1. Add `query = query.isNotEmpty("courseTitle")` to the query chain so blank/empty titles never leave Realm.
2. Replace `query.findAll()` with `query.sort("courseTitle", Sort.ASCENDING).findAll()` and delete the `filter { !it.courseTitle.isNullOrBlank() }` step.
3. Keep only a stable `sortedBy { it.isMyCourse }` (stable sort preserves the Realm title order) on the copied list, since `isMyCourse` is `@Transient` and cannot be a query field.
4. Confirm the courses list ordering (my courses grouping plus alphabetical titles) is unchanged in the UI.
:::

### Collapse duplicated double-query team lookups into one OR query
`getTeamByDocumentIdOrTeamId` and `getTeamByIdOrTeamId` are byte-identical and each issues up to two sequential Realm queries to resolve one record. A single query with an `or()` predicate in one shared private helper halves the lookups and removes the duplicate method body.

:codex-file-citation[codex-file-citation]{line_range_start=395 line_range_end=405 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L395-L405"}

:::task-stub{title="Use a single OR query for team lookups by _id or teamId in TeamsRepositoryImpl"}
1. Add a private `suspend fun findTeamByAnyId(id: String): RealmMyTeam?` that runs one query: `equalTo("_id", id).or().equalTo("teamId", id).findFirst()` (via `withRealm` + `copyFromRealm`, consistent with the file's existing helpers).
2. Make both `getTeamByDocumentIdOrTeamId` and `getTeamByIdOrTeamId` delegate to the new helper, keeping the blank-id guard.
3. Leave the public interface unchanged so no caller files are touched in this PR.
:::

### Use the injected dispatcher abstraction in ChatDetailFragment
`loadNewsConversations` hardcodes `Dispatchers.IO` even though the project provides a `DispatcherProvider` abstraction (already injected in classes like `DictionaryActivity` and `DownloadWorker`). Switching keeps dispatcher usage consistent and makes the fragment testable without touching behavior.

:codex-file-citation[codex-file-citation]{line_range_start=342 line_range_end=357 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L342-L357"}

:::task-stub{title="Inject DispatcherProvider into ChatDetailFragment and drop hardcoded Dispatchers.IO"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `ChatDetailFragment`.
2. Replace `withContext(Dispatchers.IO)` at the conversation-parsing call with `withContext(dispatcherProvider.io)`.
3. Remove the now-unused `kotlinx.coroutines.Dispatchers` import.
:::

### Replace hardcoded Dispatchers.IO in MainApplication with the injected provider
`MainApplication` injects a `dispatcherProvider` and uses it in places, but `isServerReachable` still defaults its parameter to a hardcoded `Dispatchers.IO` and `performDeferredInitialization` launches directly on `Dispatchers.IO`. Aligning these two spots completes dispatcher hygiene in the app entry point without changing scheduling behavior.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=163 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L160-L163"}

:codex-file-citation[codex-file-citation]{line_range_start=237 line_range_end=243 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L237-L243"}

:::task-stub{title="Route MainApplication coroutine launches through the injected DispatcherProvider"}
1. Change `performDeferredInitialization` to launch with the instance's injected `dispatcherProvider.io` instead of `Dispatchers.IO`.
2. For the companion `isServerReachable`, source the default dispatcher from the existing `CoreDependenciesEntryPoint` lookup already performed inside the function (or have the few internal callers pass `dispatcherProvider.io` explicitly, as line 384 already does), and delete the hardcoded `kotlinx.coroutines.Dispatchers.IO` default.
3. Remove any `Dispatchers` import that becomes unused.
:::

### Cache the survey-reminder SharedPreferences in SurveysRepositoryImpl
The same preference file is re-fetched with `context.getSharedPreferences(PREF_SURVEY_REMINDERS, ...)` five times across the reminder helpers in one class. A single lazily-initialized field removes the repeated framework lookups and trims duplicate lines.

:codex-file-citation[codex-file-citation]{line_range_start=409 line_range_end=409 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L409-L409"}

:codex-file-citation[codex-file-citation]{line_range_start=443 line_range_end=463 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L443-L463"}

:::task-stub{title="Cache survey reminder SharedPreferences in a lazy field in SurveysRepositoryImpl"}
1. Add `private val reminderPrefs: SharedPreferences by lazy { context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE) }`.
2. Replace the five inline `context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE)` calls (lines 409, 443, 451, 458, 463) with `reminderPrefs`.
:::

### Inject the download-status SharedPreferences instead of fetching it manually
`DownloadService` fetches its preferences inside the download coroutine and `DownloadWorker` (already a `@HiltWorker` with constructor injection) duplicates the same manual `getSharedPreferences` call. Providing the prefs file once from `SharedPreferencesModule` removes both manual constructions and keeps the two consumers in lockstep on the file name.

:codex-file-citation[codex-file-citation]{line_range_start=100 line_range_end=101 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt#L100-L101"}

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt#L34-L35"}

:::task-stub{title="Provide download SharedPreferences via Hilt for DownloadService and DownloadWorker"}
1. Add a `@Named("downloadPrefs")` (or dedicated qualifier) `SharedPreferences` provider for `DownloadService.PREFS_NAME` in `SharedPreferencesModule`.
2. In `DownloadService`, field-inject the qualified prefs and delete the `getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` assignment inside `onStartCommand`'s coroutine.
3. In `DownloadWorker`, replace the manual `context.getSharedPreferences(...)` property initializer with the qualified constructor parameter.
:::

### Deduplicate EntryPoint lookups and hardcoded dispatcher in SyncTimeLogger
`saveSummaryToRealm` resolves `CoreDependenciesEntryPoint` twice per sync (once for `SharedPrefManager`, once again inside the coroutine for `ServerUrlMapper`) and launches on a hardcoded `Dispatchers.IO`. One lookup reused inside a single coroutine removes redundant DI resolution on every sync completion.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L74-L80"}

:::task-stub{title="Resolve CoreDependenciesEntryPoint once in SyncTimeLogger.saveSummaryToRealm"}
1. Move the `EntryPointAccessors.fromApplication(...)` call inside the launched coroutine and store it in a single `entryPoint` val.
2. Read both `sharedPrefManager()` and `serverUrlMapper()` from that one `entryPoint`, deleting the duplicate accessor call on line 75.
3. Replace the hardcoded `Dispatchers.IO` in the `applicationScope.launch` with the io dispatcher exposed via the entry point's dispatcher provider (matching how `MainApplication` resolves it), removing the `Dispatchers` import if unused.
:::

### Cache the SharedPrefManager lookup in ThemeManager
`ThemeManager.getSpm` performs an `EntryPointAccessors.fromApplication` resolution on every call, and it is invoked for every theme read/write — including theme application at startup. Caching the singleton after the first lookup turns repeated DI graph resolutions into a field read.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt#L13-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/9e396d3b17a6ba22e635c323073ab8ca1ed35968/app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt#L47-L51"}

:::task-stub{title="Memoize the SharedPrefManager entry-point lookup in ThemeManager"}
1. Add a `@Volatile private var spm: SharedPrefManager? = null` field to the `ThemeManager` object.
2. Change `getSpm` to return the cached instance when present and only call `EntryPointAccessors.fromApplication(context.applicationContext, ...)` on first use, storing the result (the dependency is an application-scoped singleton, so caching is safe).
3. Leave `getCurrentThemeMode` / `setThemeMode` call sites unchanged.
:::

### Testing
Each task is intentionally confined to its own file (plus at most one DI module), so all ten can ship as parallel PRs in a single review round without overlapping hunks. For every PR: run `./gradlew assembleDefaultDebug` and `./gradlew assembleLiteDebug`, then smoke-test the touched screen offline — dictionary search, team voices/discussions, course filtering, team detail lookup, chat history paging, app cold start, survey reminders, resource downloads, sync-summary logging, and theme switching respectively. The repository-layer changes (Voices, Courses, Teams) deserve a before/after check that list contents and ordering are identical, since they swap in-memory filtering for query predicates.
