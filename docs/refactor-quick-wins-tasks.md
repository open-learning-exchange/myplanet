# Refactor Round: Performance Quick Wins & Inefficiency Removal (10 Tasks)

Scope: low-hanging fruit only — micro-optimizations and obvious inefficiencies that unblock the larger roadmap (data layer, DI, dispatchers, DiffUtil, observers). Each task touches a disjoint file set so all 10 PRs can be reviewed and merged in one round without conflicts. No new unused code; no use cases; no Jetpack additions.

### Remove redundant per-admin lookup that ignores its own prefetched batch map in TeamsRepositoryImpl
`getMembersWithLeadersFirst` already batch-fetches all matching admins into `adminFromRealmMap` via a single `in()` query, then the very next loop ignores that map and re-queries Realm per admin with `findByField` — a textbook N+1 that its own fix is sitting two lines above. The same file also builds manual `beginGroup`/`or()`/`equalTo` chains for ID lists in `getJoinRequestsInfo` and `getTeamNamesByIds` where Realm's `in()` operator (already used at line 1327) does the same in one predicate.

:codex-file-citation[codex-file-citation]{line_range_start=1326 line_range_end=1338 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1326-L1338"}

:codex-file-citation[codex-file-citation]{line_range_start=477 line_range_end=511 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L477-L511"}

:::task-stub{title="TeamsRepositoryImpl: use prefetched admin map and replace OR-chains with Realm in()"}
1. In the `validAdmins` loop (~line 1330), replace the per-admin `findByField(RealmUser::class.java, "name", ...)` call with a lookup into the existing `adminFromRealmMap`, falling back to the in-memory `admin` object when the map has no entry.
2. In `getJoinRequestsInfo` (~line 477), replace the `beginGroup`/`forEachIndexed`/`or()`/`equalTo("_id", id)`/`endGroup` chain with a single `in("_id", requestIds.toTypedArray())` predicate.
3. Apply the same `in()` replacement in `getTeamNamesByIds` (~line 501).
4. Run the existing TeamsRepositoryImpl unit tests to confirm behavior is unchanged.
:::

### Replace manual OR-chain query building with Realm in() in NotificationsRepositoryImpl
`getTeamNotifications` builds two `beginGroup`/`forEachIndexed`/`or()` chains over `teamIds` to emulate an IN clause; Realm's native `in()` operator expresses this in one call, avoids building an O(n) predicate tree per query, and reads cleaner. This is the same micro-pattern as the Teams task but kept as a separate PR because it is a different file.

:codex-file-citation[codex-file-citation]{line_range_start=390 line_range_end=417 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L390-L417"}

:::task-stub{title="NotificationsRepositoryImpl: use Realm in() for teamId batch queries"}
1. In the `RealmTeamNotification` query (~line 391), replace the manual `beginGroup`/`or()`/`equalTo("parentId", id)` loop with `in("parentId", teamIds.toTypedArray())`.
2. In the `RealmNews` chat-count query (~line 408), replace the equivalent loop with `in("viewableId", teamIds.toTypedArray())`.
3. Verify no other manual OR-chain remains in this file and run the NotificationsRepositoryImpl unit tests.
:::

### Batch the per-course submission and exam queries in ProgressRepositoryImpl.fetchCourseData
`fetchCourseData` runs two Realm queries (submissions and exams) inside the loop over `mycourses`, producing 2N dispatcher round-trips and 2N detach passes for N enrolled courses; the data can be fetched in two batched queries before the loop and grouped in memory by courseId. This is a straight N+1 removal with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L55-L82"}

:::task-stub{title="ProgressRepositoryImpl: hoist per-course queries out of the fetchCourseData loop"}
1. Before the `mycourses.forEach` loop, fetch all exam submissions for the user with one `queryList(RealmSubmission::class.java)` filtered by `userId` and `type == "exam"`, then group them in memory by matching `parentId` against each courseId (preserving the existing `contains` semantics).
2. Fetch all `RealmStepExam` rows for the collected `courseIds` with one `in("courseId", ...)` query and group by `courseId`.
3. Inside the loop, replace the two per-course queries with lookups into the two prebuilt maps.
4. Run the ProgressRepositoryImpl unit tests to confirm the emitted JsonArray is unchanged.
:::

### Stop detaching the entire library table just to compute a count in ResourcesRepositoryImpl
`countLibrariesNeedingUpdate` runs `queryList` — which `copyFromRealm`-detaches every matching `RealmMyLibrary` with its full object graph — only to call `.size` on a filtered list; `getDownloadSuggestionList` does the same full-table detach for its fallback path. Since `needToUpdate()` compares two fields and cannot be a Realm predicate, the cheap fix is counting/filtering on the live (non-detached) results inside a `withRealm` block and detaching only the survivors where objects are actually returned.

:codex-file-citation[codex-file-citation]{line_range_start=177 line_range_end=185 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L177-L185"}

:codex-file-citation[codex-file-citation]{line_range_start=441 line_range_end=459 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L441-L459"}

:::task-stub{title="ResourcesRepositoryImpl: count and filter needToUpdate() on live results before detaching"}
1. Rewrite `countLibrariesNeedingUpdate` to use `withRealm { realm -> ... }` directly: run the same filtered query, then `count { it.needToUpdate() }` on the live `RealmResults` and return the Int — no `copyFromRealm` of any object.
2. In `getDownloadSuggestionList`, perform the `needToUpdate()` filter on live results inside `withRealm` and detach (copy) only the filtered subset, for both the user-scoped query and the fallback all-public query.
3. Apply the same live-filter-then-detach pattern to the other `filterLibrariesNeedingUpdate` call sites in this file (~lines 140–150) where the full list is not needed.
4. Run the ResourcesRepositoryImpl unit tests.
:::

### Eliminate the double refresh per sync event and per-call regex compilation in ResourcesFragment
On every realtime sync update `onDataUpdated` triggers a full `refreshResourcesData()` reload, and because `shouldAutoRefresh` also returns true, `RealtimeSyncHelper` additionally calls `adapter.refreshWithDiff()` — two refresh paths for one event. In the same file, `normalizeText` compiles `"\\p{Mn}+".toRegex()` on every call and is invoked once per resource per search keystroke, so the pattern should be a compiled companion constant.

:codex-file-citation[codex-file-citation]{line_range_start=651 line_range_end=655 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L651-L655"}

:codex-file-citation[codex-file-citation]{line_range_start=661 line_range_end=684 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L661-L684"}

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L36-L42"}

:::task-stub{title="ResourcesFragment: single refresh per sync update and hoisted normalization regex"}
1. Override `shouldAutoRefresh(table)` to return false (matching the pattern already used by CoursesFragment, SurveyFragment, and FeedbackListFragment), so `onDataUpdated`'s `refreshResourcesData()` remains the single refresh path.
2. Hoist the `"\\p{Mn}+"` regex out of `normalizeText` into a private compiled `Regex` constant in the fragment's companion object and reference it in the function.
3. Manually verify search filtering and post-sync list refresh still behave correctly.
:::

### Cache the SharedPrefManager lookup behind NetworkUtils.getCustomDeviceName
`getCustomDeviceName` performs a Hilt `EntryPointAccessors.fromApplication` lookup plus a SharedPreferences read on every call, and it is called once per serialized record in upload serializers (RealmMyLibrary, RealmRating, RealmNewsLog, RealmApkLog, RealmSearchActivity, RealmUser, RealmMyPersonal, MyPlanet) — so a 50-item upload batch repeats the lookup 50 times. The file already demonstrates the fix for its coroutine scope: a lazily cached entry-point property.

:codex-file-citation[codex-file-citation]{line_range_start=179 line_range_end=182 path=app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt#L179-L182"}

:codex-file-citation[codex-file-citation]{line_range_start=215 line_range_end=218 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L215-L218"}

:::task-stub{title="NetworkUtils: lazily cache the SharedPrefManager used by getCustomDeviceName"}
1. Add a `private val sharedPrefManager: SharedPrefManager by lazy { ... }` to the NetworkUtils object, resolved once via the existing `CoreDependenciesEntryPoint` (mirroring the `coroutineScope` property at lines 27–31).
2. Change `getCustomDeviceName` to read from the cached property instead of calling `EntryPointAccessors.fromApplication` per invocation (keep the `Context` parameter for signature stability if callers require it, but stop using it for the lookup).
3. Confirm no caller depends on picking up a mid-session entry-point change (the entry point is a singleton component, so caching is safe) and run the NetworkUtils unit tests.
:::

### Use payload-based partial rebinds for selection changes in UserArrayAdapter
On each selection tap `UserArrayAdapter` calls `notifyItemChanged` without a payload for both the old and new rows, forcing full rebinds that re-run `ImageUtils.loadProfileImage` (a Glide load) just to change a background color. ServerAddressAdapter, ResourcesAdapter, and CoursesAdapter in this codebase already use a `SELECTION_PAYLOAD` + `onBindViewHolder(payloads)` pattern; this adapter should match it.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=62 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt#L40-L62"}

:::task-stub{title="UserArrayAdapter: selection updates via payload instead of full rebind"}
1. Add a private `SELECTION_PAYLOAD` constant to the adapter (same convention as ServerAddressAdapter's).
2. Pass the payload in both `notifyItemChanged` calls in the click listener (lines 60–61).
3. Override `onBindViewHolder(holder, position, payloads)` to handle the payload by updating only the item background color, delegating to the full bind when payloads are empty.
4. Manually verify selecting users in the login user list updates highlight without avatar flicker.
:::

### Route isServerReachable through DispatcherProvider and cache its entry-point lookup in MainApplication
`isServerReachable` hard-codes `Dispatchers.IO` as a default parameter — bypassing the project's injectable `DispatcherProvider` convention that tests rely on — and performs an `EntryPointAccessors.fromApplication` lookup on every reachability check, which runs repeatedly during server probing and sync. Both fixes are local to this companion object.

:codex-file-citation[codex-file-citation]{line_range_start=181 line_range_end=197 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L181-L197"}

:::task-stub{title="MainApplication: DispatcherProvider-backed dispatcher and cached entry point for isServerReachable"}
1. Add a lazily cached `CoreDependenciesEntryPoint` property in the companion object and use it in `isServerReachable` instead of the per-call `EntryPointAccessors.fromApplication` lookup.
2. Change the `ioDispatcher` default parameter to resolve from the cached entry point's `dispatcherProvider().io` instead of the hard-coded `Dispatchers.IO`, keeping the parameter overridable so existing tests can still inject a test dispatcher.
3. Check that `SyncTimeLogger`-style callers and any test overrides still compile, and run the affected unit tests.
:::

### Drop the unnecessary Dispatchers.Main coroutine launch in DialogUtils guest dialog
The become-member button click handler wraps a plain `Intent` construction and `context.startActivity` in `MainApplication.applicationScope.launch(Dispatchers.Main)`, but click callbacks already run on the main thread — the coroutine adds a hard-coded dispatcher reference, an application-scope hop, and a frame of latency for zero benefit. Removing it also clears one of the last two hard-coded `Dispatchers.*` usages outside DispatcherProvider.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L55-L63"}

:::task-stub{title="DialogUtils: remove redundant Dispatchers.Main launch around startActivity"}
1. In the `becomeMember` click listener, remove the `MainApplication.applicationScope.launch(Dispatchers.Main)` wrapper and execute the intent construction and `context.startActivity` directly. If `profileDbHandler.getUserModel()` turns out to require a background call, scope it with the injected DispatcherProvider instead of a raw dispatcher.
2. Remove the now-unused `Dispatchers` (and possibly `launch`) imports.
3. Manually verify the guest "become a member" flow still opens BecomeMemberActivity.
:::

### Move DictionaryActivity's raw Realm queries behind a repository
DictionaryActivity is the last UI class querying Realm through raw `databaseService.withRealmAsync { realm.where(...) }` calls, which blocks declaring the data layer "clean" and keeps query logic in the view layer. Both queries (a count and a case-insensitive single-word lookup) map one-to-one onto existing `RealmRepository` helpers (`count`, `findFirstCopy`), so this is a mechanical extraction with an interface, an impl, and one DI binding.

:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/be00b24209ecb3f37c6940a9c3e3a0cb22ed4ba2/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L145-L160"}

:::task-stub{title="Extract DictionaryRepository and remove the last raw Realm access from the UI layer"}
1. Create `DictionaryRepository` with two suspend functions: `getDictionaryCount(): Long` and `findWord(word: String): RealmDictionary?`.
2. Create `DictionaryRepositoryImpl` extending `RealmRepository(databaseService, realmDispatcher)`, implementing the count via the base `count` helper and the lookup via `findFirstCopy` with a case-insensitive `equalTo("word", ...)`.
3. Bind the interface to the impl in `RepositoryModule` following the existing `@Binds` pattern.
4. Inject the repository into DictionaryActivity, replace the two `databaseService.withRealmAsync` blocks, and remove the now-unused `DatabaseService`/Realm imports from the activity.
5. Add a small MockK unit test for the new repository mirroring an existing sibling repository test, and run the unit test suite.
:::

### Testing

Every task should pass `./gradlew testDefaultDebugUnitTest` (what CI runs on push) before opening its PR; tasks touching repositories should extend or run the existing MockK suites named after the file (e.g. `TeamsRepositoryImplTest`), and the two UI tasks (ResourcesFragment, UserArrayAdapter) need a quick manual smoke check of search/refresh and selection highlighting since instrumented tests don't run in CI. All 10 tasks touch disjoint files, so they can be reviewed and merged in any order within the round without rebasing.
