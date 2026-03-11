# PR Merge Plan

## Sorted by Importance (1-100)

### PR #11791: Add jsonUtils and sharedPrefManager tests and test dependencies (fixes #11829) (Score: 75)
- Files changed: 4
- Additions: 1109, Deletions: 4

### PR #11859: 🧹 fix negative max in course progress division (Score: 70)
- Files changed: 1
- Additions: 2, Deletions: 2

### PR #11856: sync: use explicit Realm insert handlers (fixes #11855) (Score: 70)
- Files changed: 1
- Additions: 29, Deletions: 28

### PR #11854: sync: remove redundant prefs save call (fixes #11853) (Score: 70)
- Files changed: 1
- Additions: 1, Deletions: 2

### PR #11852: ⚡ [Performance] Fix N+1 RealmAnswer query in CoursesRepositoryImpl (Score: 70)
- Files changed: 1
- Additions: 41, Deletions: 22

### PR #11865: 🧹 Code Health Improvement: Fix Time Formatter Minute Padding (Score: 65)
- Files changed: 1
- Additions: 8, Deletions: 8

### PR #11860: 🧹 code health improvement: Fix progress bar calculation bounds in MarkdownDialogFragment (Score: 65)
- Files changed: 1
- Additions: 2, Deletions: 2

### PR #11817: ⚡ Refactor uploadResource to resolve N+1 database queries (Score: 65)
- Files changed: 1
- Additions: 63, Deletions: 34

### PR #11869: all: improve null-safety and avoid NPEs (fixes #11863) (Score: 55)
- Files changed: 29
- Additions: 135, Deletions: 108

### PR #11872: Refactor VoicesRepositoryImpl to use base executeTransaction (Score: 45)
- Files changed: 1
- Additions: 38, Deletions: 48

### PR #11871: Refactor executeTransaction in SubmissionsRepositoryImpl (Score: 45)
- Files changed: 1
- Additions: 72, Deletions: 76

### PR #11870: Refactor HealthRepositoryImpl executeTransaction (Score: 45)
- Files changed: 1
- Additions: 2, Deletions: 2

### PR #11868: ⚡ [perf] optimize N+1 queries in CoursesRepositoryImpl (Score: 45)
- Files changed: 1
- Additions: 18, Deletions: 15

### PR #11862: 🧹 [code health] Optimize calculating available storage in FileUtils (Score: 45)
- Files changed: 1
- Additions: 4, Deletions: 6

### PR #11858: 🧹 [code health improvement] Refactor float rating check in RatingsFragment (Score: 45)
- Files changed: 1
- Additions: 2, Deletions: 2

## Conflict Analysis (Sequential Merge)

### PR #11791 (Add jsonUtils and sharedPrefManager tests and test dependencies (fixes #11829))
- No detected file conflicts with earlier PRs in this sequence.

### PR #11859 (🧹 fix negative max in course progress division)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11856 (sync: use explicit Realm insert handlers (fixes #11855))
- No detected file conflicts with earlier PRs in this sequence.

### PR #11854 (sync: remove redundant prefs save call (fixes #11853))
- Potential conflicts detected with:
  - File `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` was modified in PR #11856. Manual resolution required in `continueInsert`.

### PR #11852 (⚡ [Performance] Fix N+1 RealmAnswer query in CoursesRepositoryImpl)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11865 (🧹 Code Health Improvement: Fix Time Formatter Minute Padding)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11860 (🧹 code health improvement: Fix progress bar calculation bounds in MarkdownDialogFragment)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11817 (⚡ Refactor uploadResource to resolve N+1 database queries)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11869 (all: improve null-safety and avoid NPEs (fixes #11863))
- No detected file conflicts with earlier PRs in this sequence.

### PR #11872 (Refactor VoicesRepositoryImpl to use base executeTransaction)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11871 (Refactor executeTransaction in SubmissionsRepositoryImpl)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11870 (Refactor HealthRepositoryImpl executeTransaction)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11868 (⚡ [perf] optimize N+1 queries in CoursesRepositoryImpl)
- Potential conflicts detected with:
  - File `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt` was modified in PR #11852. Both optimize N+1 query in `getExamObject`. Manual resolution required. They have logical conflict since they both rewrite the same function to fix the same/related performance issue.

### PR #11862 (🧹 [code health] Optimize calculating available storage in FileUtils)
- No detected file conflicts with earlier PRs in this sequence.

### PR #11858 (🧹 [code health improvement] Refactor float rating check in RatingsFragment)
- Potential conflicts detected with:
  - File `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt` was modified in PR #11869. 11869 changed it to `== 0.0f` and 11858 changes it to `== 0f`. Manual resolution required.
