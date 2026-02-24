# Refactoring Tasks Checklist - Copy/Paste Ready

## 10 Granular Tasks for Repository Boundary Cleanup

### ✅ Task 1: Move Health Examination Transactions to HealthRepository
**Files:** `AddExaminationActivity.kt` (L262-348), `HealthRepositoryImpl.kt`
**Steps:**
1. Add `suspend fun saveExamination(examination, health, user): Result<Unit>` to HealthRepository
2. Implement in HealthRepositoryImpl using withRealm pattern
3. Refactor AddExaminationActivity.saveData() to call repository method
4. Remove mRealm field and DatabaseService injection from Activity
5. Test health examination creation flow

---

### ✅ Task 2: Fix SyncActivity Flow Collection Lifecycle
**File:** `SyncActivity.kt` (L773-781)
**Steps:**
1. Wrap collect in registerReceiver() with repeatOnLifecycle(STARTED)
2. Store launch job in class-level Job? variable
3. Add job?.cancel() in onPause()/onDestroy()
4. Test activity lifecycle for memory leaks

---

### ✅ Task 3: Replace applicationScope with lifecycleScope in SyncActivity
**File:** `SyncActivity.kt` (L654-669)
**Steps:**
1. Replace MainApplication.applicationScope.launch with lifecycleScope.launch (L658)
2. Replace .launchIn(applicationScope) with .launchIn(lifecycleScope) (L669)
3. Add CancellationException handling
4. Test login flow stops correctly on activity destruction

---

### ✅ Task 4: Convert InlineResourceAdapter to ListAdapter+DiffUtil
**File:** `InlineResourceAdapter.kt` (L27-30)
**Steps:**
1. Extend ListAdapter<RealmMyLibrary, ViewHolder>(DiffUtils.itemCallback())
2. Remove resources field from constructor and getItemCount()
3. Update onBindViewHolder to use getItem(position)
4. Update call sites to use submitList()
5. Test inline resources display in courses

---

### ✅ Task 5: Create LifeViewModel
**Files:** New `LifeViewModel.kt`, update `LifeFragment.kt`
**Steps:**
1. Create LifeViewModel with @HiltViewModel and LifeRepository injection
2. Expose StateFlow<List<RealmLife>> for life items
3. Move business logic from Fragment to ViewModel
4. Update Fragment to inject ViewModel and collect with repeatOnLifecycle
5. Remove direct lifeRepository injection from Fragment
6. Test life feature data loading

---

### ✅ Task 6: Create PersonalsViewModel
**Files:** New `PersonalsViewModel.kt`, update `PersonalsFragment.kt`
**Steps:**
1. Create PersonalsViewModel with @HiltViewModel
2. Inject PersonalsRepository, UploadManager, UserSessionManager
3. Expose StateFlow for personals list and upload state
4. Move data logic from Fragment lifecycle to ViewModel
5. Update Fragment to observe ViewModel state
6. Test personals display and upload

---

### ✅ Task 7: Tighten TeamResourcesFragment Repository Boundary
**Files:** `TeamResourcesFragment.kt` (L100-125), `TeamsRepositoryImpl.kt`
**Steps:**
1. Add getAvailableResourcesToAdd(teamId) to TeamsRepository interface
2. Implement in TeamsRepositoryImpl (inject ResourcesRepository, filter)
3. Update showResourceListDialog() to call new method
4. Remove resourcesRepository injection from Fragment
5. Test adding resources to teams

---

### ✅ Task 8: Move Resource Batch Sync to ResourcesRepository
**Files:** `SyncManager.kt` (L664-712), `ResourcesRepositoryImpl.kt`
**Steps:**
1. Add batchInsertResources(resources: List<JsonObject>) to ResourcesRepository
2. Implement in ResourcesRepositoryImpl using withRealm batch transaction
3. Update SyncManager.resourceTransactionSync() to call repository
4. Remove direct DatabaseService usage from sync section
5. Test resource synchronization performance

---

### ✅ Task 9: Inject ServerUrlMapper via Hilt
**Files:** 14+ files with manual instantiation (MyHealthFragment L83, FeedbackListFragment L47, etc.)
**Steps:**
1. Add @Singleton and @Inject constructor() to ServerUrlMapper
2. Provide in ServiceModule.kt if needed
3. Replace all `ServerUrlMapper()` with `@Inject lateinit var serverUrlMapper`
4. Update MainApplication injection
5. Test server URL mapping across features

---

### ✅ Task 10: Move Chat Sync to ChatRepository
**Files:** `TransactionSyncManager.kt` (L240-312), `ChatRepositoryImpl.kt`
**Steps:**
1. Add syncInsertChatHistory(chatData: JsonArray) to ChatRepository
2. Implement in ChatRepositoryImpl using withRealm
3. Inject ChatRepository into TransactionSyncManager
4. Replace insertToChat/insertDocs calls with repository method
5. Test chat message synchronization

---

## Recommended Execution Order

1. **Task 9** (ServerUrlMapper DI) - Wide but shallow changes
2. **Tasks 2 & 3** (SyncActivity) - One file, critical fixes
3. **Task 4** (Adapter) - Isolated UI component
4. **Task 1** (Health) - Isolated feature area
5. **Tasks 5 & 6** (ViewModels) - New files, low conflict risk
6. **Task 7** (Team resources) - Builds on patterns
7. **Task 8** (Resource sync) - Service layer extraction
8. **Task 10** (Chat sync) - Similar to Task 8

---

## Key Metrics

- **Total Tasks:** 10
- **Estimated Time:** 15-25 hours total
- **Average PR Size:** 1-3 hours of work
- **Files Touched:** ~30 total across all tasks
- **New Files Created:** 2 ViewModels
- **Lines Changed:** ~500-800 total (minimal modifications)

---

## Success Criteria

✅ All repository access goes through proper layers  
✅ No direct mRealm access in UI components  
✅ All Flow collectors use repeatOnLifecycle  
✅ All dependencies injected via Hilt  
✅ All adapters use ListAdapter+DiffUtil  
✅ Proper MVVM separation with ViewModels  
✅ No memory leaks on lifecycle transitions  
✅ All features work as before (no regressions)

