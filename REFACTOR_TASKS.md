# Refactoring Tasks - Priority Queue

This document outlines 10 granular refactoring tasks to reinforce repository boundaries and improve code organization. Each task is designed to be small, isolated, and easily reviewable.

---

## Task 1: Create DictionaryRepository Interface + Implementation

**Priority:** High | **Scope:** Single feature | **Risk:** Low

**Problem:** `DictionaryActivity` directly injects `DictionaryDao`, bypassing the repository layer.

**Action:**
1. Create `DictionaryRepository` interface with methods:
   - `count(): Long`
   - `insertAll(entities: List<DictionaryEntity>)`
   - `findByWord(word: String): DictionaryEntity?`
2. Create `DictionaryRepositoryImpl` that wraps `DictionaryDao`
3. Register in `RepositoryModule`
4. Refactor `DictionaryActivity` to inject `DictionaryRepository` instead of `DictionaryDao`

**Files touched:** `DictionaryActivity.kt`, new `DictionaryRepository.kt`, new `DictionaryRepositoryImpl.kt`, `RepositoryModule.kt`

**Review checklist:**
- [ ] Interface properly abstracts DAO
- [ ] Activity only knows about repository, not DAO
- [ ] Test if existing

---

## Task 2: Move `UploadConfigs` DAOs to Repository Wrappers

**Priority:** High | **Scope:** Single file | **Risk:** Low

**Problem:** `UploadConfigs` directly injects 8 different DAOs (`ApkLogDao`, `SearchActivityDao`, `CourseProgressDao`, `ResourceActivityDao`, `SubmitPhotosDao`, `NewsLogDao`, `TeamLogDao`, `TeamTaskDao`).

**Action:** Create thin repository wrappers or extend existing repositories with methods:
1. `getPendingUploads()` pattern methods → move to `ActivitiesRepository`
2. Activity log DAOs → extend `ActivitiesRepository`
3. `TeamTaskDao` operations → extend `TeamsRepository`

**Rationale:** Consolidate upload-related data access through repositories instead of direct DAO injection in a service.

**Files touched:** `UploadConfigs.kt`, relevant repository interfaces/implementations

---

## Task 3: Remove unused `notifyDataSetChanged()` in PagerAdapters

**Priority:** Medium | **Scope:** 2 files | **Risk:** Low

**Problem:** `CoursesPagerAdapter` and `TeamPagerAdapter` use deprecated `notifyDataSetChanged()`.

**Action:**
1. Migrate `CoursesPagerAdapter` to use `ListAdapter` with `DiffUtils.itemCallback`
2. Migrate `TeamPagerAdapter` to use `ListAdapter` with `DiffUtils.itemCallback`

**Files touched:** `CoursesPagerAdapter.kt`, `TeamPagerAdapter.kt`

**Review checklist:**
- [ ] No more `notifyDataSetChanged()`
- [ ] Uses existing `DiffUtils.itemCallback`
- [ ] Proper `submitList()` calls

---

## Task 4: Add `DictionaryDao` to RoomModule Provider

**Priority:** Low | **Scope:** DI module | **Risk:** None (additive)

**Problem:** `DictionaryDao` is injected directly but not provided via `RoomModule`.

**Action:**
Add `provideDictionaryDao(db: AppDatabase)` function to `RoomModule.kt` if not already present.

**Files touched:** `RoomModule.kt`

---

## Task 5: Extract `CoursesPagerAdapter` DIFF_CALLBACK to Use `DiffUtils`

**Priority:** Low | **Scope:** Single adapter | **Risk:** Low

**Problem:** `CoursesPagerAdapter` likely has inline DiffUtil logic.

**Action:** Create a shared `ITEM_CALLBACK` using `DiffUtils.standardItemCallback` or `DiffUtils.itemCallback`.

**Files touched:** `CoursesPagerAdapter.kt`

---

## Task 6: Add Repository Interface Method for `DashboardViewModel.loadUserContent`

**Priority:** Medium | **Scope:** ViewModel + Repositories | **Risk:** Low

**Problem:** `DashboardViewModel` uses `withContext(dispatcherProvider.io)` wrapping repository calls, some direct Flow collection patterns could be cleaner.

**Action:**
1. Review `DashboardViewModel.loadUserContent()`:
   - `resourcesRepository.getMyLibrary()` → already in repo ✓
   - `coursesRepository.getMyCoursesFlow()` → already Flow ✓
   - `teamsRepository.getMyTeamsFlow()` → already Flow ✓
2. Ensure all state management happens in StateFlow inside ViewModel
3. Document that `flowOn()` before `collect()` is redundant (repository already handles dispatcher)

**Files touched:** `DashboardViewModel.kt`

---

## Task 7: Create `ActivitiesRepository` Methods for Upload Config DAOs

**Priority:** Medium | **Scope:** Repository | **Risk:** Low

**Problem:** `UploadConfigs` directly queries `SearchActivityDao`, `ResourceActivityDao`, `NewsLogDao`, `TeamLogDao`.

**Action:** Add to `ActivitiesRepository` interface:
- `getPendingSearchActivityUploads(): List<SearchActivity>`
- `getPendingResourceActivityUploads(): List<ResourceActivity>`
- `getPendingNewsLogUploads(): List<NewsLog>`
- `getPendingTeamLogUploads(): List<TeamLog>`

**Files touched:** `ActivitiesRepository.kt`, `ActivitiesRepositoryImpl.kt`, `UploadConfigs.kt`

---

## Task 8: Remove `Lazy` Wrapper from `TeamsSyncRepository` in UploadConfigs

**Priority:** Low | **Scope:** Single injection | **Risk:** Low

**Problem:** `UploadConfigs` uses `Lazy<TeamsSyncRepository>` unnecessarily.

**Action:** Change to direct injection `teamsSyncRepository: TeamsSyncRepository`.

**Files touched:** `UploadConfigs.kt`

**Rationale:** `TeamsSyncRepository` is already `@Singleton`, no lazy loading needed.

---

## Task 9: Add `DictionaryEntity` to Model Package Imports Cleanup

**Priority:** Low | **Scope:** Model imports | **Risk:** None

**Problem:** `DictionaryActivity` imports `DictionaryEntity` but this should be encapsulated by repository.

**Action:** After Task 1, verify only `DictionaryRepository` is imported in `DictionaryActivity`.

**Files touched:** `DictionaryActivity.kt` (after Task 1)

---

## Task 10: Audit `BaseResourceFragment` for Repository Boundary Leaks

**Priority:** Medium | **Scope:** Base class | **Risk:** Medium

**Problem:** `BaseResourceFragment` injects multiple repositories directly. Some helper methods might be better placed in those repositories.

**Action:**
1. Review methods in `BaseResourceFragment`:
   - `removeFromShelf()` → already uses repositories ✓
   - `addToLibrary()` → already uses repositories ✓
   - `addAllToLibrary()` → already uses repositories ✓
2. Consider if any data transformation logic should move to repository
3. Document the repository usage pattern is correct

**Files touched:** `BaseResourceFragment.kt`

---

## Summary Table

| # | Task | Files | Priority | Risk | Lines (est) |
|---|------|-------|----------|------|-------------|
| 1 | DictionaryRepository | 4 | High | Low | ~150 |
| 2 | UploadConfigs DAOs → Repos | 2-3 | High | Low | ~100 |
| 3 | PagerAdapters → ListAdapter | 2 | Medium | Low | ~100 |
| 4 | DictionaryDao in RoomModule | 1 | Low | None | ~5 |
| 5 | PagerAdapter DiffUtil | 1 | Low | Low | ~20 |
| 6 | DashboardViewModel cleanup | 1 | Medium | Low | ~50 |
| 7 | ActivitiesRepository methods | 3 | Medium | Low | ~80 |
| 8 | Remove Lazy wrapper | 1 | Low | Low | ~1 |
| 9 | Import cleanup | 1 | Low | None | ~5 |
| 10 | BaseResourceFragment audit | 1 | Medium | Medium | ~30 |

**Total estimated changes:** ~540 lines across ~15 files

---

## Merge Conflict Prevention Strategy

To avoid merge conflicts during PR review:

1. **One PR per task** - Each task is isolated to specific files
2. **No overlapping changes** - Tasks are mutually exclusive
3. **PR order:** Execute in numbered order (1-10)
4. **Branch naming:** `refactor/task-{number}-{short-description}`

**Example branch names:**
- `refactor/01-dictionary-repository`
- `refactor/02-upload-configs-dao-consolidation`
- etc.

---

## Testing Requirements

For each PR:
- [ ] Unit test for new repository methods
- [ ] Existing tests still pass
- [ ] Manual smoke test for affected UI (if any)
