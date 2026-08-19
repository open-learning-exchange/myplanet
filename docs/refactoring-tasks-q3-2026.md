# myPlanet Refactoring Tasks - Q3 2026

**Goal**: Reinforce repository boundaries, move data functions from UI to repositories, and address cross-feature data leaks.

**Constraints**:
- ~10 PRs per round
- Low-hanging fruits only
- Granular, easily reviewable changes
- Minimize merge conflicts

---

## Task 1: Move `sortNews` from VoicesFragment to VoicesViewModel

**File**: `ui/voices/VoicesFragment.kt`  
**Issue**: `sortNews()` method (lines 200-210) does data sorting in the Fragment layer

**Action**: Move sorting logic to `VoicesViewModel` as a private method or inline it into the filter flow

**Repository Impact**: None (this is UI logic that should be in ViewModel)

---

## Task 2: Move `downloadResourcesForNews` from VoicesFragment to VoicesViewModel

**File**: `ui/voices/VoicesFragment.kt`  
**Issue**: `downloadResourcesForNews()` (lines 190-197) does resource coordination in Fragment

**Action**: Move to `VoicesViewModel` or extract as a standalone utility function that takes repository dependencies

**Repository Impact**: May require adding a method to ResourcesRepository if not already present

---

## Task 3: Extract `filterNews` from VoicesViewModel to VoicesRepository

**File**: `ui/voices/VoicesViewModel.kt`  
**Issue**: `filterNews()` (lines 96-140) is complex filtering logic in ViewModel

**Action**: Move filtering to `VoicesRepository` as a suspend function, keeping the flow in ViewModel for reactive updates

**Repository Impact**: Add `filterNews()` method to VoicesRepository interface

---

## Task 4: Move `sortCourses` from CoursesViewModel to CoursesRepository

**File**: `ui/courses/CoursesViewModel.kt`  
**Issue**: `sortCourses()` (lines 76-90) and `processCourses()` (lines 92-111) do course processing in ViewModel

**Action**: Move sorting/filtering logic to `CoursesRepository` as a suspend function

**Repository Impact**: Add `sortCourses()` and `processCourses()` methods to CoursesRepository

---

## Task 5: Add `getFilterFacets` to ResourcesRepository Interface

**File**: `ui/resources/ResourcesViewModel.kt`  
**Issue**: `getFilterFacets()` (lines 67-69) calls repository but logic could be co-located

**Action**: Verify the method exists in ResourcesRepository interface and impl, ensure it's properly documented

**Repository Impact**: Already exists but verify interface completeness

---

## Task 6: Standardize MemberMenuAdapter to use DiffUtils.itemCallback

**File**: `ui/teams/members/MembersAdapter.kt`  
**Issue**: `MemberMenuAdapter` (lines 207-216) extends ArrayAdapter directly instead of using consistent DiffUtil pattern

**Action**: Consider replacing with a simple ListAdapter using `DiffUtils.itemCallback` for consistency

**Repository Impact**: None

---

## Task 7: Move `calculateIndividualProgress` and `calculateCommunityProgress` from DashboardViewModel to ProgressRepository

**File**: `ui/dashboard/DashboardViewModel.kt`  
**Issue**: Progress calculation logic (lines 119-131) is in ViewModel

**Action**: Move to `ProgressRepository` as pure calculation methods

**Repository Impact**: Add methods to ProgressRepository

---

## Task 8: Extract `buildPrecomputedChats` and `searchChats` from ChatViewModel to ChatRepository

**File**: `ui/chat/ChatViewModel.kt`  
**Issue**: Chat search/precomputation logic (lines 162-208) is heavy ViewModel work

**Action**: Move to `ChatRepository` as suspend functions or flow transformers

**Repository Impact**: Add search methods to ChatRepository interface

---

## Task 9: Add `applyFilters` to TeamsRepository

**File**: `ui/teams/TeamViewModel.kt`  
**Issue**: `applyFilters()` (lines 102-112) does team filtering in ViewModel

**Action**: Move to `TeamsRepository` as a suspend function

**Repository Impact**: Add `filterTeams()` method to TeamsRepository

---

## Task 10: Review and Document Cross-Feature Dependencies in Repositories

**Files**: Multiple repository implementations  
**Issue**: TeamsRepositoryImpl and CoursesRepositoryImpl inject many DAOs and cross-feature dependencies

**Action**: Audit each repository for:
- Unnecessary DAO injections (should only access data they own)
- Cross-feature repository dependencies
- Document boundary violations

**Repository Impact**: Potential interface tightening, moving shared methods to appropriate repositories

---

## Priority Order (for PR sequencing)

1. Task 7 (Dashboard progress calc) - **Smallest change, clear boundary**
2. Task 5 (Resources filter) - **Verify existing implementation**
3. Task 6 (MemberMenuAdapter) - **Simple adapter refactor**
4. Task 9 (Teams filter) - **Clear extraction**
5. Task 1 (VoicesFragment sort) - **Simple move**
6. Task 4 (Courses sort) - **Medium complexity**
7. Task 2 (Voices resources) - **Medium complexity**
8. Task 3 (Voices filter) - **Complex, do later**
9. Task 8 (Chat search) - **Complex, do later**
10. Task 10 (Audit) - **Documentation, do last**

---

## Notes

- Tasks 1-4, 6-9 are **data function moves** (UI → Repository)
- Task 5 is a **verification task** (confirm existing implementation)
- Task 10 is a **documentation/audit task**
- Avoid tasks that touch multiple files heavily to reduce merge conflicts
- Each PR should be self-contained where possible
