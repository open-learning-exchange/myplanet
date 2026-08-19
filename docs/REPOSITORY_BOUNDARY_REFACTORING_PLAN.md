# Repository Boundary Refactoring Plan
## 10 Low-Risk, High-Impact Tasks for Layer Separation

**Goal:** Reinforce repository boundaries between layers, eliminate cross-feature data leaks, tighten repository interfaces, and migrate data functions from UI/services into repositories.

**Constraints:** 
- Each task ≤ ~10 PRs per review round
- Low hanging fruit only
- No unused code additions
- Granular, easily reviewable changes
- Minimize merge conflicts

---

## Task 1: Audit and Document Cross-Layer Data Access Violations

**Focus:** Identify all direct data access from UI layer bypassing repositories

**Actions:**
- Search for direct `DatabaseService`, `RealmInstance`, or `ApiInterface` usage in `/ui/*` fragments and activities
- Document each violation with file location and suggested repository migration path
- Create a simple markdown table: `File | Direct Access | Should Use Repository | Priority`
- Target files with >3 violations first (likely `SyncActivity`, `ProcessUserDataActivity`, `DashboardActivity`)

**Expected Output:** Documentation file `docs/REPOSITORY_BOUNDARY_AUDIT.md` listing all violations

**PR Count Estimate:** 1-2 PRs (documentation only)

**Risk Level:** None (read-only audit)

---

## Task 2: Migrate SharedPrefManager Calls from UI Fragments to UserRepository

**Focus:** Centralize user preference access through UserRepository interface

**Target Files:**
- `ui/community/CommunityTabFragment.kt` - `sharedPrefManager` field injection
- `ui/community/HomeCommunityDialogFragment.kt` - `sharedPrefManager` field injection
- `ui/chat/ChatHistoryFragment.kt` - `sharedPrefManager` field injection
- `ui/chat/ChatDetailFragment.kt` - `sharedPrefManager` field injection
- `ui/teams/TeamFragment.kt` - `sharedPrefManager` field injection

**Changes:**
- Add new methods to `UserRepository` interface for needed preference operations
- Remove `@Inject lateinit var sharedPrefManager` from target fragments
- Inject `UserRepository` instead and call repository methods
- Keep `SettingsViewModel` and `LoginActivity` unchanged for now (larger scope)

**PR Strategy:** Group by feature area (community, chat, teams) = 3 PRs

**PR Count Estimate:** 3-4 PRs

**Risk Level:** Low (well-defined interface boundary)

---

## Task 3: Move Download Logic from UI to DownloadRepository

**Focus:** Consolidate download initiation logic currently scattered in UI components

**Target Files:**
- `ui/sync/SyncActivity.kt` - calls `DownloadUtils.openDownloadService`
- `ui/viewer/ResourceViewerViewModel.kt` - calls `DownloadUtils.openDownloadService`
- `ui/dictionary/DictionaryActivity.kt` - calls `DownloadUtils.openDownloadService`

**Changes:**
- Add `initiateDownload(context, urls, isMultiple)` method to `DownloadRepository` interface
- Implement in `DownloadRepositoryImpl` delegating to existing `DownloadUtils`
- Replace direct `DownloadUtils.openDownloadService` calls with repository method
- Keep `DownloadService` implementation unchanged (service layer remains separate)

**PR Strategy:** One PR per calling site = 3 small PRs

**PR Count Estimate:** 3 PRs

**Risk Level:** Low (simple delegation pattern)

---

## Task 4: Extract BroadcastService Dependency from UI Activities

**Focus:** Remove direct `BroadcastService` field injection from activities

**Target Files:**
- `ui/sync/SyncActivity.kt` - `lateinit var broadcastService: BroadcastService`
- `ui/dictionary/DictionaryActivity.kt` - `override lateinit var broadcastService`

**Changes:**
- Determine if `BroadcastService` is actually used beyond lifecycle management
- If used for sync events: route through `SyncRepository` event flow instead
- If only lifecycle: consider removing and using standard Android lifecycle observers
- Update DI module if needed to stop injecting into activities

**PR Strategy:** Single coordinated PR since both files are sync-related

**PR Count Estimate:** 1-2 PRs

**Risk Level:** Medium (requires understanding broadcast usage patterns)

---

## Task 5: Consolidate UploadToShelfService and FileUploader Access

**Focus:** Route upload operations through `UploadRepository` instead of direct service access

**Target Files:**
- `ui/sync/ProcessUserDataActivity.kt` - injects both `UploadManager` and `UploadToShelfService`

**Changes:**
- Review current `UploadRepository` interface - already exists with basic methods
- Add missing upload methods that UI currently calls directly on services
- Remove `@Inject lateinit var uploadToShelfService` from `ProcessUserDataActivity`
- Use `UploadRepository` for all upload operations
- Keep `UploadManager` internal to repository implementation

**PR Strategy:** Single focused PR on one activity

**PR Count Estimate:** 2 PRs (interface update + usage migration)

**Risk Level:** Low-Medium (upload logic is sensitive but well-contained)

---

## Task 6: Standardize DiffUtil ItemCallback Definitions in Adapters

**Focus:** Ensure all adapters use `DiffUtils.itemCallback` consistently and move callbacks outside class

**Target Files (audit first):**
- Check all adapters extending `RecyclerView.Adapter` for inline `DiffUtil.ItemCallback`
- Verify existing users of `DiffUtils.itemCallback` follow best practices

**Changes:**
- For adapters with inline callbacks: extract to companion object `val DIFF_CALLBACK`
- Ensure all use `DiffUtils.itemCallback` factory (not direct `DiffUtil.ItemCallback` object)
- Apply consistent pattern: `companion object { private val DIFF_CALLBACK = DiffUtils.itemCallback(...) }`
- Target 5-7 adapters per PR to keep changes small

**PR Strategy:** Group by UI feature area (feedback, submissions, health, etc.)

**PR Count Estimate:** 4-5 PRs (batches of adapters)

**Risk Level:** Very Low (mechanical refactoring, no logic changes)

---

## Task 7: Audit ViewModel Scope Coroutine Usage for Proper Dispatcher Selection

**Focus:** Verify `viewModelScope.launch` calls use appropriate dispatchers

**Target Files:** Search results showing `viewModelScope` usage:
- `ui/feedback/FeedbackListViewModel.kt`, `FeedbackDetailViewModel.kt`
- `ui/ratings/RatingsViewModel.kt`
- `ui/settings/SettingsViewModel.kt` (already uses `dispatcherProvider.io`)
- `ui/submissions/SubmissionViewModel.kt`, `SubmissionDetailViewModel.kt`, `SubmissionListViewModel.kt`
- `ui/health/HealthViewModel.kt`, `HealthExaminationViewModel.kt`
- `ui/notifications/NotificationsViewModel.kt`

**Changes:**
- Identify `viewModelScope.launch` without explicit dispatcher (defaults to Main)
- Add `dispatcherProvider.io` for database/network operations
- Add `dispatcherProvider.default` for CPU-intensive work
- Keep Main dispatcher for UI state updates only
- Ensure `DispatcherProvider` is injected into ViewModels needing it

**PR Strategy:** Group by feature (feedback, submissions, health) = 3 PRs

**PR Count Estimate:** 3-4 PRs

**Risk Level:** Low (adding dispatchers is safe, improves performance)

---

## Task 8: Identify and Remove Long-Lived Observer Registrations

**Focus:** Find observers/listeners registered but potentially not unregistered

**Target Areas:**
- Review `collectWhenStarted` usage patterns - ensure proper lifecycle binding
- Check for `observeForever` or manual observer registration without removal
- Audit `Flow.collect` in fragments without lifecycle scope
- Review any `registerReceiver`, `addOnPageChangeCallback`, fragment lifecycle callbacks

**Known Candidates from Initial Scan:**
- `ui/voices/VoicesFragment.kt` - has `unregisterAdapterDataObserver` (verify it's called)
- `ui/teams/resources/TeamResourcesFragment.kt` - unregisters fragment lifecycle callbacks
- `ui/viewer/ResourceViewerFragment.kt` - unregisters audio receiver (verify cleanup path)

**Changes:**
- Add missing `onDestroyView` or `onStop` cleanup calls
- Convert manual observers to `repeatOnLifecycle` or `collectWhenStarted` patterns
- Document any intentional long-lived observers with comments

**PR Strategy:** One PR per problematic file (should be few)

**PR Count Estimate:** 2-3 PRs

**Risk Level:** Medium (memory leak prevention is important but requires careful testing)

---

## Task 9: Tighten Repository Interface Methods - Remove Implementation Leakage

**Focus:** Ensure repository interfaces don't expose implementation details

**Target Interfaces to Review:**
- `TeamsRepository.kt` - large interface (72+ lines), check for impl-specific methods
- `UserRepository.kt` - check for methods returning Realm objects vs domain models
- `CoursesRepository.kt` - verify methods return clean domain types
- `SubmissionsRepository.kt` - check for exporter pattern leakage

**Changes:**
- Replace methods returning `RealmObject` subclasses with domain model classes
- Remove methods that expose internal caching mechanisms
- Ensure async operations return `Flow<T>` or `suspend` functions, not callbacks
- Split large interfaces if they mix concerns (sync vs CRUD vs business logic)

**PR Strategy:** One repository interface per PR with corresponding impl update

**PR Count Estimate:** 3-4 PRs (Teams, User, Submissions repositories)

**Risk Level:** Medium-High (interface changes ripple to all consumers)

---

## Task 10: Move Service-Side Data Operations to Appropriate Repositories

**Focus:** Extract data manipulation logic from services into repositories

**Target Services:**
- `services/UserDataWorker.kt` - likely has user data operations that belong in `UserRepository`
- `services/SubmissionsUploader.kt` - submission logic could move to `SubmissionsRepository`
- `services/VoicesLabelManager.kt` - voices operations to `VoicesRepository`

**Changes:**
- For each service, identify pure data operations (no Android framework dependencies)
- Add corresponding methods to appropriate repository interface
- Move implementation to repository impl class
- Service calls repository instead of doing data work directly
- Keep Android-specific work (WorkManager, Notifications) in services

**PR Strategy:** One service extraction per PR

**PR Count Estimate:** 3 PRs (UserDataWorker, SubmissionsUploader, VoicesLabelManager)

**Risk Level:** Medium (requires understanding service responsibilities)

---

## Execution Order Recommendation

1. **Task 1** (Audit) - Do first, informs all other tasks
2. **Task 6** (DiffUtil) - Safest, mechanical changes, builds momentum
3. **Task 2** (SharedPrefManager) - Clear boundary improvement
4. **Task 3** (DownloadRepository) - Simple delegation pattern
5. **Task 7** (Dispatchers) - Performance improvement, low risk
6. **Task 4** (BroadcastService) - Moderate complexity
7. **Task 5** (UploadRepository) - Depends on Task 4 learnings
8. **Task 8** (Observer Cleanup) - Requires careful testing
9. **Task 10** (Service Extraction) - Builds on repository strengthening
10. **Task 9** (Interface Tightening) - Do last as it may require rework based on earlier tasks

## Merge Conflict Mitigation Strategies

- **Coordinate ownership:** Assign each team member specific feature areas (feedback, submissions, health, etc.)
- **Sequential merging:** Complete all PRs in one task before starting next task
- **Small PRs:** Each PR touches ≤5 files ideally
- **Rebase daily:** Stay current with main branch to catch conflicts early
- **Feature flags:** For risky changes, wrap in feature flag for easy rollback

## Success Metrics

- [ ] Zero direct `DatabaseService` access from UI layer
- [ ] Zero direct `ApiInterface` access from UI layer  
- [ ] All adapters use `DiffUtils.itemCallback` pattern
- [ ] All `viewModelScope.launch` specify explicit dispatcher
- [ ] Repository interfaces return only domain models (no Realm objects)
- [ ] Services delegate data operations to repositories

---

*Generated for PR Review Round - Focus: Repository Boundaries & Layer Separation*
