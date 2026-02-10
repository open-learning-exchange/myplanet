# 10 Repository Boundary Refactoring Tasks - Quick Reference

## Task Overview

All tasks focus on **reinforcing repository boundaries** and **moving data operations from UI/Service layers into Repository layer**. Each task is granular, independent, and easily reviewable (~9.99 PR size).

---

## Task 1: Extract User Creation to UserRepository
**File:** `BecomeMemberActivity.kt` (line 146-158)
**Issue:** Direct DataService instantiation in UI
**Fix:** Move becomeMember logic to UserRepositoryImpl
**Steps:**
1. Add `suspend fun createMember(userJson: JsonObject): Result<String>` to UserRepository
2. Implement in UserRepositoryImpl with error handling
3. Update BecomeMemberActivity to use repository
4. Remove DataService dependency from Activity

---

## Task 2: Create HealthRepository for Examination Data
**File:** `AddExaminationActivity.kt` (lines 84-100, 249-268)
**Issue:** Direct Realm transactions in Activity (beginTransaction, commitTransaction, queries)
**Fix:** Create new HealthRepository extending RealmRepository
**Steps:**
1. Create HealthRepository interface + HealthRepositoryImpl
2. Add methods: getHealthExamination, saveExamination, getUserHealthProfile
3. Move all Realm logic from Activity to RepositoryImpl
4. Inject HealthRepository into Activity
5. Replace mRealm calls with repository methods

---

## Task 3: Move Reply Operations to VoicesRepository
**File:** `ReplyActivity.kt` (lines 48-60)
**Issue:** Direct DatabaseService.withRealm() calls in UI
**Fix:** Add reply methods to VoicesRepository
**Steps:**
1. Identify DatabaseService.withRealm() usages
2. Add methods to VoicesRepository interface
3. Implement in VoicesRepositoryImpl using RealmRepository
4. Update ReplyActivity to use repository
5. Remove DatabaseService injection

---

## Task 4: Fix Threading in ConfigurationsRepository
**File:** `ConfigurationsRepositoryImpl.kt` (lines 40-78, 87-150)
**Issue:** Using Dispatchers.Main for network I/O operations
**Fix:** Use Dispatchers.IO for network, Main only for callbacks
**Steps:**
1. Replace `withContext(Dispatchers.Main)` with `Dispatchers.IO` for network calls
2. Update checkVersion() to use IO dispatcher
3. Switch to Main only before listener callbacks
4. Test health check and version check

---

## Task 5: Make getUserModel Async-Safe
**File:** `UserRepositoryImpl.kt` (lines 234-244)
**Issue:** Synchronous Realm access without proper dispatcher
**Fix:** Deprecate and migrate to suspend version
**Steps:**
1. Deprecate getUserModel() in interface
2. Update all callers to use getUserModelSuspending()
3. Remove deprecated method after migration
4. Verify no sync Realm on UI thread

---

## Task 6: Extract Base Fragment Queries to Repositories
**File:** `BaseRecyclerParentFragment.kt` (lines 14-73)
**Issue:** Direct mRealm.where() queries in base UI class
**Fix:** Move queries to ResourcesRepository and CoursesRepository
**Steps:**
1. Add getMyLibraryItems/getOurLibraryItems to ResourcesRepository
2. Add getMyCourses/getOurCourses to CoursesRepository
3. Update getList() to call repositories instead of mRealm
4. Keep filter logic in base class
5. Test all list displays

---

## Task 7: Remove mRealm from BaseResourceFragment
**File:** `BaseResourceFragment.kt` (lines 60-105)
**Issue:** Protected mRealm field enables direct DB access in all subclasses
**Fix:** Remove mRealm field entirely
**Steps:**
1. Audit all subclasses for mRealm usage
2. Add repository methods for each usage
3. Remove mRealm field
4. Remove requireRealmInstance/isRealmInitialized helpers
5. Update all fragments to use repositories
6. Test resource browsing

---

## Task 8: Add ViewModel to ExamTakingFragment
**File:** `ExamTakingFragment.kt` (lines 43-80)
**Issue:** Complex state (answerCache map) and business logic in Fragment
**Fix:** Create ExamTakingViewModel
**Steps:**
1. Create ExamTakingViewModel with StateFlow
2. Move answerCache, listAns, exam loading to ViewModel
3. Inject SurveysRepository and SubmissionsRepository
4. Update Fragment to observe ViewModel state
5. Move repository calls to ViewModel

---

## Task 9: Add ViewModel to MyHealthFragment
**File:** `MyHealthFragment.kt` (lines 59-80)
**Issue:** Manual Job tracking and sync state management in Fragment
**Fix:** Create HealthViewModel with StateFlow
**Steps:**
1. Create HealthViewModel with sync status StateFlow
2. Move sync coordination to ViewModel
3. Inject UserRepository and SyncManager
4. Replace manual Jobs with viewModelScope
5. Update Fragment to observe state

---

## Task 10: Add ViewModel to SurveyFragment
**File:** `SurveyFragment.kt` (lines 38-78)
**Issue:** surveyInfoMap, bindingDataMap, manual Job tracking in Fragment
**Fix:** Create SurveyViewModel
**Steps:**
1. Create SurveyViewModel with StateFlow
2. Move maps and loadSurveysJob to ViewModel
3. Inject SurveysRepository and SyncManager
4. Update Fragment to use by viewModels()
5. Move filtering and checks to ViewModel

---

## Key Principles

✅ **DO:**
- Use RealmRepository base class for DB operations
- Use proper dispatchers (IO for network/DB, Main for UI callbacks)
- Inject repositories via Hilt
- Use ViewModels for complex state
- Keep changes minimal and focused

❌ **DON'T:**
- Add use cases (keep it simple)
- Add unused code
- Make complicated multi-file changes
- Skip testing affected features

---

## Testing Strategy

For each task:
1. Test affected feature before changes (baseline)
2. Build with `./gradlew assembleDefaultDebug`
3. Test affected feature after changes
4. Verify no functionality broken

**Key testing areas:**
- User auth (Tasks 1, 5)
- Health exams (Tasks 2, 9)
- Voices/replies (Task 3)
- Server checks (Task 4)
- Courses/resources (Tasks 6, 7)
- Exams (Task 8)
- Surveys (Task 10)
