# PR Review and Merge Plan

Ordered by importance (highest first).

## [#11803](https://github.com/open-learning-exchange/myplanet/pull/11803) - Standardize CoroutineScope usage in UI adapters
**Importance:** 100/100
**Stats:** +170 / -126 across 4 files

### ✅ No Collisions Detected

---

## [#11791](https://github.com/open-learning-exchange/myplanet/pull/11791) - Add jsonUtils and sharedPrefManager tests and test dependencies
**Importance:** 100/100
**Stats:** +1105 / -0 across 4 files

### ✅ No Collisions Detected

---

## [#11788](https://github.com/open-learning-exchange/myplanet/pull/11788) - Refactor CourseDetailFragment to use CourseDetailViewModel
**Importance:** 100/100
**Stats:** +163 / -55 across 2 files

### ✅ No Collisions Detected

---

## [#11781](https://github.com/open-learning-exchange/myplanet/pull/11781) - all: smoother shared preferences managing (fixes #11780)
**Importance:** 100/100
**Stats:** +109 / -170 across 21 files

### ✅ No Collisions Detected

---

## [#11766](https://github.com/open-learning-exchange/myplanet/pull/11766) - 🧪 add unit tests for TimeUtils
**Importance:** 87/100
**Stats:** +162 / -0 across 3 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11791** (Add jsonUtils and sharedPrefManager tests and test dependencies)
  - `gradle/libs.versions.toml`
  - `app/build.gradle`

---

## [#11799](https://github.com/open-learning-exchange/myplanet/pull/11799) - Migrate list selections to StateFlow ViewModels
**Importance:** 84/100
**Stats:** +84 / -68 across 4 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`

---

## [#11794](https://github.com/open-learning-exchange/myplanet/pull/11794) - Refactor BaseRecyclerParentFragment Realm queries to use Repositories
**Importance:** 59/100
**Stats:** +61 / -21 across 9 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt`
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11799** (Migrate list selections to StateFlow ViewModels)
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`

---

## [#11789](https://github.com/open-learning-exchange/myplanet/pull/11789) - fix: optimize SubmissionsAdapter ViewHolder
**Importance:** 58/100
**Stats:** +51 / -61 across 1 files

### ✅ No Collisions Detected

---

## [#11790](https://github.com/open-learning-exchange/myplanet/pull/11790) - Optimize Realm deletion operations in BaseRecyclerFragment
**Importance:** 40/100
**Stats:** +47 / -30 across 1 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11799** (Migrate list selections to StateFlow ViewModels)
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11794** (Refactor BaseRecyclerParentFragment Realm queries to use Repositories)
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`

---

## [#11785](https://github.com/open-learning-exchange/myplanet/pull/11785) - Refactor deleteCourseProgress into CoursesRepository
**Importance:** 37/100
**Stats:** +36 / -26 across 3 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11799** (Migrate list selections to StateFlow ViewModels)
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11794** (Refactor BaseRecyclerParentFragment Realm queries to use Repositories)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`
- **#11790** (Optimize Realm deletion operations in BaseRecyclerFragment)
  - `app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt`

---

## [#11807](https://github.com/open-learning-exchange/myplanet/pull/11807) - fix: wrap SurveyFragment collectors in repeatOnLifecycle
**Importance:** 35/100
**Stats:** +35 / -31 across 1 files

### ✅ No Collisions Detected

---

## [#11786](https://github.com/open-learning-exchange/myplanet/pull/11786) - Refactor CourseProgressActivity to use ViewModel
**Importance:** 30/100
**Stats:** +40 / -12 across 2 files

### ✅ No Collisions Detected

---

## [#11811](https://github.com/open-learning-exchange/myplanet/pull/11811) - Normalize UploadToShelfService DI and threading
**Importance:** 28/100
**Stats:** +27 / -22 across 2 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt`

---

## [#11812](https://github.com/open-learning-exchange/myplanet/pull/11812) - ⚡ Optimize N+1 Query in Course Answers Retrieval
**Importance:** 25/100
**Stats:** +36 / -10 across 1 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11794** (Refactor BaseRecyclerParentFragment Realm queries to use Repositories)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- **#11785** (Refactor deleteCourseProgress into CoursesRepository)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`

---

## [#11795](https://github.com/open-learning-exchange/myplanet/pull/11795) - Refactor LifeAdapter to exclusively use submitList for list manipulation
**Importance:** 24/100
**Stats:** +19 / -21 across 2 files

### ✅ No Collisions Detected

---

## [#11792](https://github.com/open-learning-exchange/myplanet/pull/11792) - Simplify batch write-path for TransactionSync
**Importance:** 24/100
**Stats:** +24 / -20 across 1 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`

---

## [#11810](https://github.com/open-learning-exchange/myplanet/pull/11810) - 🔒 [security fix] Implement secure password storage using EncryptedSharedPreferences and Tink
**Importance:** 23/100
**Stats:** +29 / -6 across 3 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11781** (all: smoother shared preferences managing (fixes #11780))
  - `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt`

---

## [#11798](https://github.com/open-learning-exchange/myplanet/pull/11798) - Refactor UserProfileFragment to combine flow observers
**Importance:** 22/100
**Stats:** +8 / -32 across 1 files

### ✅ No Collisions Detected

---

## [#11797](https://github.com/open-learning-exchange/myplanet/pull/11797) - Optimize and serialize Realm Flow Dispatch in RealmRepository
**Importance:** 22/100
**Stats:** +27 / -13 across 1 files

### ✅ No Collisions Detected

---

## [#11800](https://github.com/open-learning-exchange/myplanet/pull/11800) - Refactor NotificationsFragment to wrap flow collections in repeatOnLifecycle
**Importance:** 21/100
**Stats:** +22 / -16 across 1 files

### ✅ No Collisions Detected

---

## [#11809](https://github.com/open-learning-exchange/myplanet/pull/11809) - ⚡ Optimize Course Questions Retrieval (N+1 Query)
**Importance:** 19/100
**Stats:** +24 / -11 across 1 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11794** (Refactor BaseRecyclerParentFragment Realm queries to use Repositories)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- **#11785** (Refactor deleteCourseProgress into CoursesRepository)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`
- **#11812** (⚡ Optimize N+1 Query in Course Answers Retrieval)
  - `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`

---

## [#11806](https://github.com/open-learning-exchange/myplanet/pull/11806) - Refactor TakeCourseFragment: Inline withContext(Dispatchers.Main) and clean up imports
**Importance:** 19/100
**Stats:** +14 / -20 across 1 files

### ✅ No Collisions Detected

---

## [#11802](https://github.com/open-learning-exchange/myplanet/pull/11802) - Fix lifecycle scope in CoursesProgressFragment
**Importance:** 19/100
**Stats:** +9 / -5 across 1 files

### ✅ No Collisions Detected

---

## [#11787](https://github.com/open-learning-exchange/myplanet/pull/11787) - Optimize notification formatting and mark-read state updates
**Importance:** 19/100
**Stats:** +31 / -4 across 1 files

### ✅ No Collisions Detected

---

## [#11801](https://github.com/open-learning-exchange/myplanet/pull/11801) - Refactor: Wrap PersonalsFragment collectLatest in repeatOnLifecycle
**Importance:** 17/100
**Stats:** +7 / -3 across 1 files

### ✅ No Collisions Detected

---

## [#11805](https://github.com/open-learning-exchange/myplanet/pull/11805) - Refactor coroutine scope in TeamsTasksFragment
**Importance:** 16/100
**Stats:** +4 / -4 across 1 files

### ✅ No Collisions Detected

---

## [#11784](https://github.com/open-learning-exchange/myplanet/pull/11784) - Refactor TeamsAdapter: remove unused teamStatusCache local state
**Importance:** 16/100
**Stats:** +0 / -8 across 1 files

### ✅ No Collisions Detected

---

## [#11783](https://github.com/open-learning-exchange/myplanet/pull/11783) - Refactor InlineResourceAdapter to use custom DiffUtils
**Importance:** 16/100
**Stats:** +13 / -16 across 1 files

### ✅ No Collisions Detected

---

## [#11804](https://github.com/open-learning-exchange/myplanet/pull/11804) - Fix: Replace lifecycleScope with viewLifecycleOwner.lifecycleScope in LifeFragment adapter callbacks
**Importance:** 14/100
**Stats:** +2 / -2 across 1 files

### ⚠️ Potential Collisions
This PR touches files modified by the following PRs that will be merged before it:

- **#11795** (Refactor LifeAdapter to exclusively use submitList for list manipulation)
  - `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt`

---
