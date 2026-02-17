# myPlanet Refactoring Tasks - Repository Boundary Reinforcement

This document outlines 10 granular, low-hanging-fruit tasks to reinforce repository boundaries and move data logic from UI/service layers into repositories.

---

### Task 1: Remove mRealm exposure from BaseResourceFragment

BaseResourceFragment exposes a protected mRealm field allowing 20+ fragment subclasses direct database access, violating repository boundaries. This field should be private and all data operations delegated to injected repositories.

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L61-L61"}

:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=100 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L91-L100"}

:::task-stub{title="Make mRealm private in BaseResourceFragment"}
1. Change mRealm visibility from protected to private in BaseResourceFragment.kt line 61
2. Audit all subclasses for direct mRealm usage and confirm they use injected repositories instead
3. Run linter and build to verify no compilation errors
4. Test one representative fragment (e.g., ResourcesFragment) to ensure functionality preserved
:::

---

### Task 2: Move health examination query from AddExaminationActivity to HealthRepository

AddExaminationActivity performs direct Realm query for health examinations. This data access logic belongs in HealthRepository with a proper repository method.

:codex-file-citation[codex-file-citation]{line_range_start=136 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L136-L159"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt#L1-L9"}

:::task-stub{title="Add getExaminationById to HealthRepository"}
1. Add suspend fun getExaminationById(id: String): RealmHealthExamination? to HealthRepository interface
2. Implement method in HealthRepositoryImpl using withRealm helper from RealmRepository
3. Replace direct mRealm.where() call at line 138 in AddExaminationActivity with repository call
4. Remove unused mRealm field initialization from AddExaminationActivity if no longer needed
5. Test health examination loading functionality
:::

---

### Task 3: Extract user session data queries from UserSessionManager to UserRepository

UserSessionManager contains deprecated userModel property that directly accesses Realm. All data access should go through UserRepository following the established pattern.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L41-L51"}

:codex-file-citation[codex-file-citation]{line_range_start=107 line_range_end=150 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L107-L150"}

:::task-stub{title="Remove deprecated userModel from UserSessionManager"}
1. Find all usages of profileDbHandler.userModel or userSessionManager.userModel in UI layer
2. Replace with suspend call to userSessionManager.getUserModel() within proper coroutine scope
3. Mark getUserModelCopy() method as deprecated with same message
4. Add @Deprecated annotation to existing userModel property at line 42
5. Verify no new usages introduced and existing deprecated usages have migration path documented
:::

---

### Task 4: Consolidate Dispatchers.Main usage in UI fragments

53 instances of withContext(Dispatchers.Main) exist in UI layer when lifecycleScope.launch already uses Main dispatcher by default, creating unnecessary context switches.

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=130 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L123-L130"}

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L52-L77"}

:::task-stub{title="Remove redundant withContext(Dispatchers.Main) in 5 fragments"}
1. Select 5 fragments with simple patterns: ResourcesFragment, CoursesFragment, TeamDetailFragment, LifeFragment, PersonalsFragment
2. Remove withContext(Dispatchers.Main) wrapper where parent is lifecycleScope.launch or viewLifecycleOwner.lifecycleScope.launch
3. Ensure suspend repository calls remain within Dispatchers.IO context blocks only
4. Run builds and spot-test affected fragments to verify UI updates still occur correctly
5. Document pattern in code review for remaining 48 instances to address in future PRs
:::

---

### Task 5: Move download suggestion query from BaseResourceFragment to ResourcesRepository

BaseResourceFragment calls resourcesRepository.getDownloadSuggestionList but the broadcast receiver pattern with data fetching in line 127 mixes concerns.

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=136 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L122-L136"}

:codex-file-citation[codex-file-citation]{line_range_start=184 line_range_end=238 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L184-L238"}

:::task-stub{title="Create ResourcesRepository method for download suggestions with user context"}
1. Review getDownloadSuggestionList implementation in ResourcesRepository
2. Add optional parameter for automatic user ID retrieval if needed for common cases
3. Simplify receiver.onReceive callback at lines 125-134 to use simplified repository call
4. Update showDownloadDialog to accept pre-fetched list to separate data fetching from UI display
5. Test download suggestion dialog flow remains functional
:::

---

### Task 6: Add HealthRepository methods for save and update operations

AddExaminationActivity has saveData() method with direct Realm transactions that should be moved to HealthRepository following the RealmRepository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=50 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/AddExaminationActivity.kt#L50-L70"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt#L1-L9"}

:::task-stub{title="Add saveHealthExamination method to HealthRepository"}
1. Add suspend fun saveHealthExamination(examination: RealmHealthExamination, profile: RealmMyHealthProfile): Result<Unit> to HealthRepository
2. Implement in HealthRepositoryImpl with proper Realm transaction handling using withRealm
3. Move saveData() logic from AddExaminationActivity into new repository method
4. Replace saveData() call in Activity with repository.saveHealthExamination() within lifecycleScope
5. Test health examination save functionality end-to-end
:::

---

### Task 7: Replace Dispatchers.Default with Dispatchers.IO for database operations

10 usages of Dispatchers.Default found for I/O-bound operations like Realm queries, but Dispatchers.IO is the correct dispatcher for blocking I/O operations.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L1-L10"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L1-L10"}

:::task-stub{title="Replace Dispatchers.Default with Dispatchers.IO in 3 files"}
1. Update ChatDetailFragment withContext(Dispatchers.Default) to Dispatchers.IO for message processing
2. Update TeamViewModel withContext(Dispatchers.Default) to Dispatchers.IO for team data processing
3. Update SubmissionViewModel flowOn(Dispatchers.Default) to Dispatchers.IO
4. Keep MainApplication Dispatchers.Default as-is since those are CPU-bound operations
5. Run builds and test affected features (chat, teams, submissions) to verify no regressions
:::

---

### Task 8: Extract team membership check from BaseTeamFragment to TeamsRepository

BaseTeamFragment performs team membership checks directly, mixing data access logic with UI lifecycle. TeamsRepository already has some team methods but lacks membership query exposure.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L49-L78"}

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L29-L35"}

:::task-stub{title="Verify TeamsRepository.isMember is properly exposed"}
1. Confirm TeamsRepository.isMember() exists and returns proper membership status
2. Verify loadTeamDetails() in BaseTeamFragment (line 68) already uses teamsRepository.isMember correctly
3. Search for any other direct team membership queries in UI layer that bypass repository
4. Document that team membership checks should always use TeamsRepository.isMember
5. Add unit test for isMember edge cases if missing
:::

---

### Task 9: Migrate VoicesFragment direct mRealm usage to VoicesRepository

VoicesFragment checks mRealm.isInTransaction directly which is a database implementation detail that should be handled within repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L91-L97"}

:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L46-L58"}

:::task-stub{title="Remove mRealm transaction check from VoicesFragment"}
1. Review why VoicesFragment checks mRealm.isInTransaction at lines 91-97
2. Determine if transaction cleanup is needed or if it's defensive programming
3. Move transaction cleanup to repository layer or remove if repositories handle it properly
4. Alternatively, create VoicesRepository.ensureNoActiveTransaction() if truly needed
5. Remove direct mRealm access from VoicesFragment after proper handling established
:::

---

### Task 10: Consolidate ListAdapter DiffUtil usage patterns

All 40+ adapters use ListAdapter with DiffUtils.itemCallback which is excellent, but ensure consistent patterns for selection state management after recent fixes.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=72 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L60-L72"}

:codex-file-citation[codex-file-citation]{line_range_start=228 line_range_end=234 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L228-L234"}

:::task-stub{title="Document adapter state management pattern post-fixes"}
1. Review adapter memory noting recent fixes to avoid clearAllSelections() calls
2. Document pattern: New adapter instances should handle their own initialization
3. Identify 3 adapters that might still have manual state clearing: ResourcesAdapter, CoursesAdapter, VoicesAdapter
4. Verify submitList() callbacks don't manipulate old adapter state
5. Create brief code comment template for adapter initialization to guide future implementations
:::

---

## Testing Section

### Testing Strategy

For each task, follow this testing approach:

1. **Unit Testing**: If repositories are modified, verify existing repository tests still pass
2. **Build Verification**: Run `./gradlew assembleDefaultDebug` to ensure no compilation errors
3. **Smoke Testing**: Launch affected feature and perform basic operations (view list, select item, navigate)
4. **Edge Case Testing**: Test with empty data, offline mode, and guest user scenarios where applicable

### Commands

```bash
# Build both flavors
./gradlew assembleDefaultDebug assembleLiteDebug

# Run lint
./gradlew lintDefaultDebug

# If tests exist for modified areas
./gradlew testDefaultDebugUnitTest
```

### Critical Areas to Manual Test

- Task 1: Test ResourcesFragment list display and selection
- Task 2: Test health examination creation and editing
- Task 3: Test login flow and user profile access
- Task 6: Test health data save operation
- Task 8: Test team membership display in team details
- Task 9: Test voices/news post creation
