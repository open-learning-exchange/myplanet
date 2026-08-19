# myPlanet Performance Optimization Tasks
**Date:** 2026-08-19
**Base commit:** current HEAD
**Open PRs checked:** 15820, 15808, 15772, 15771, 15769, 15699, 15698, 15656, 15650, 15594, 15559, 15519, 15412, 15267, 15266, 15198, 15158, 15108

---

### 1. Remove redundant `toList()` copies in BaseVoicesFragment (roadmap 7+8)

context: `BaseVoicesFragment.kt` calls `adapterNews.submitList(adapterNews.currentList.toList())` on
lines 77 and 81. `currentList` is already a `List` — `toList()` creates an unnecessary shallow
copy on every news-update event, wasting allocations in a hot path.
files: app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt (lines 77, 81).
Do NOT touch `NewsViewModel` or any repository.
steps:
1. Line 77: change `adapterNews?.submitList(adapterNews?.currentList?.toList())` → `adapterNews?.submitList(adapterNews?.currentList)`
2. Line 81: change `adapterNews?.submitList(adapterNews?.currentList?.toList())` → `adapterNews?.submitList(adapterNews?.currentList)`
3. Confirm safe-call chain is preserved; remove any now-unused `?.toList()` in the same file
acceptance: `./gradlew testDefaultDebugUnitTest` green; voices/news screen still renders the correct
item list and reply badges after refresh and on data-change callbacks
size budget: ~2 changed lines, 1 file
out of scope: no DAO or repository changes

---

### 2. Replace regex split with `substringAfterLast` in PersonalsAdapter (roadmap 7)

context: `PersonalsAdapter.kt` line 66 uses `path?.split("\\.".toRegex())` to extract a file extension.
A single-character literal split is 5–10× faster than Regex; `substringAfterLast('.')` is the idiomatic
one-liner for the same result with zero regex overhead.
files: app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt (line 66).
steps:
1. Change `path?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.getOrNull(arr.size - 1)` → `path?.substringAfterLast('.', "")`
2. Remove the now-unused `arr` variable declaration (`val arr = …`)
3. Update the `when` branch condition: compare `path?.substringAfterLast('.', "")` instead of `arr?.get(arr.size - 1)`
4. Confirm no `Regex` imports remain unused
acceptance: `./gradlew testDefaultDebugUnitTest` green; personal resources screen still opens files
with the correct viewer based on their extension
size budget: ~3 changed lines, 1 file
out of scope: no other adapter methods

---

### 3. Replace per-keystroke regex split with string split in HealthExaminationActivity (roadmap 7)

context: `HealthExaminationActivity.kt` line 166 compiles `"/".toRegex()` inside a `doOnTextChanged`
callback. Regex instantiation on every keystroke (user typing systolic/diastolic values) is wasteful;
Kotlin's `String.split(String)` uses a plain search and is orders of magnitude faster.
files: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt (line 166).
Do NOT touch `HealthExaminationAdapter`.
steps:
1. Change `.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()` → `.split("/").dropLastWhile { it.isEmpty() }`
2. The `split` result can be used directly as a `List`; the `toTypedArray()` is only needed to use `[0]` / `[1]`
3. Remove any `Regex` import if it becomes unused
acceptance: `./gradlew testDefaultDebugUnitTest` green; blood-pressure field parses "120/80" correctly
into systolic=120 and diastolic=80; malformed input still triggers the error messages
size budget: ~2 changed lines, 1 file
out of scope: no blood-pressure validation logic changes

---

### 4. Remove redundant `toList()` on `stepMistake.keys` in CoursesProgressAdapter (roadmap 7)

context: `CoursesProgressAdapter.kt` line 46 calls `stepMistake.keys.toList()` where `stepMistake`
is already a `Map`. `keys` is a `Set`, and `toList()` creates a copy just to pass to a loop.
`for (key in stepMistake.keys)` or destructuring directly from the map is allocation-free.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt (line 46).
Do NOT touch `CoursesViewModel` or any repository.
steps:
1. Change `val keys = stepMistake.keys.toList()` → iterate `stepMistake.keys` directly in the `forEach`
   that follows (the list is only used to drive a loop)
2. Remove the `keys` variable declaration and the `toList()` allocation
acceptance: `./gradlew testDefaultDebugUnitTest` green; course-progress step list still shows
the correct mistake count per step
size budget: ~2 changed lines, 1 file
out of scope: no changes to the mistake-count display logic

---

### 5. Replace lazy-regex split with string split in HealthExaminationAdapter (roadmap 7)

context: `HealthExaminationAdapter.kt` line 69 uses `createdBy.split(colonRegex)` where
`colonRegex = ":".toRegex()` (lazy). Regex split has ~3× the overhead of `String.split` for a
single-character delimiter. This is called inside `displayNameCache.getOrPut`, so it runs once per
unique `createdBy` value — cheap but easily fixed.
files: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt (lines 69, 188).
Do NOT touch `HealthExaminationActivity`.
steps:
1. Line 69: change `createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1)` → `createdBy.split(":").dropLastWhile { it.isEmpty() }.getOrNull(1)`
2. Line 188: remove the `colonRegex` lazy delegate (or keep it if used elsewhere — verify first)
3. Remove `import … colonRegex` if the reference is gone
acceptance: `./gradlew testDefaultDebugUnitTest` green; examination display names resolve correctly
for both self-examinations and examinations by other users
size budget: ~3 changed lines, 1 file
out of scope: no changes to examination list display logic

---

### 6. Remove double-collection copies in StorageBreakdownFragment (roadmap 7)

context: `StorageBreakdownFragment.kt` lines 258–259 pass `category.extensions.toList()` and
`allKnownExtensions.toList()` to `StorageCategoryDetailFragment.newInstance`. Both `extensions`
and `allKnownExtensions` are already `ArrayList<String>` — the `toList()` on an `ArrayList`
creates an intermediate list copy before `ArrayList(...)` copies it again. 2 allocations for a
snapshot that could be 1.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt (lines 258–259).
steps:
1. Line 258: change `category.extensions.toList()` → `category.extensions`
2. Line 259: change `allKnownExtensions.toList()` → `allKnownExtensions`
3. Verify both fields are `ArrayList<String>` in the data model; if `Collection<String>` use
   `ArrayList(…)` once instead of `ArrayList(ArrayList(…))`
acceptance: `./gradlew testDefaultDebugUnitTest` green; storage-breakdown screen still opens
the correct category-detail dialog with the right extension list
size budget: ~2 changed lines, 1 file
out of scope: no changes to storage-size calculation

---

### 7. Remove redundant Set↔List conversions in NotificationsRepositoryImpl (roadmap 7)

context: `NotificationsRepositoryImpl.kt` lines 113–115 in `markNotificationsAsRead` chain
`notificationIds.toList()` then immediately `.toSet()` then `.toList()` again. The middle
`toSet()` is unnecessary — we can collect IDs directly from the DAO result list and pass
them to `markAsRead` without the round-trip through Set.
files: app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt
(lines 113–115). Do NOT touch `NotificationDao`.
steps:
1. Replace the body of `markNotificationsAsRead`:
   from: `notificationDao.getByIds(notificationIds.toList()).map { it.id }.toSet()`
   to:   `notificationDao.getByIds(notificationIds.toList()).mapTo(mutableSetOf()) { it.id }`
2. Remove the `existingIds.toList()` on the `markAsRead` call — `markAsRead` accepts `Set<String>`
   natively via the Collection overload
3. Change the `return` type and expression to return `mutableSetOf()` directly
acceptance: `./gradlew testDefaultDebugUnitTest` green; notification mark-as-read still updates
the correct notification IDs in the database and returns the right set
size budget: ~4 changed lines, 1 file
out of scope: no changes to notification DAO or data model

---

### 8. Replace per-comparison `lowercase` allocation with `compareBy` in SurveysViewModel (roadmap 7)

context: `SurveysViewModel.kt` lines 141–142 call `sortedBy { it.name?.lowercase(Locale.getDefault()) }`
and the DESC variant. This allocates a new `String` object on every comparison (log n times),
adding GC pressure for large survey lists. `compareBy(String.CASE_INSENSITIVE_ORDER)` is a
single static comparator with zero per-comparison allocation.
files: app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt (lines 141–142).
Do NOT touch `SurveyFragment` or `SurveysRepository`.
steps:
1. Line 141: change `filteredList.sortedBy { it.name?.lowercase(Locale.getDefault()) }` →
   `filteredList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })`
2. Line 142: change `filteredList.sortedByDescending { it.name?.lowercase(Locale.getDefault()) }` →
   `filteredList.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })`
3. Remove `import java.util.Locale` if it becomes unused; verify the `Locale` import is no longer needed
acceptance: `./gradlew testDefaultDebugUnitTest` green; survey TITLE_ASC/TITLE_DESC sort order is
unchanged (Apple < Banana < Zebra, case-insensitive — verified by existing tests)
size budget: ~3 changed lines, 1 file
out of scope: no changes to DATE sort or filter logic

---

### 9. Replace per-call `@"-regex` with `substringBefore` in BaseExamFragment (roadmap 7)

context: `BaseExamFragment.kt` line 89 calls `sub?.parentId?.split("@".toRegex())` to extract the
username portion of an ID like `"userId@timestamp"`. The entire chain
`split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)` compiles a new
Regex and builds two intermediate collections to grab a single character range. `substringBefore("@")`
is the zero-allocation equivalent and is semantically identical.
files: app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt (lines 89–93).
Do NOT touch `ExamTakingFragment` or `SubmissionViewModel`.
steps:
1. Replace the `if (sub?.parentId?.contains("@") == true)` branch: change the split chain to
   `sub?.parentId?.substringBefore("@")`
2. Remove the `else` branch entirely — `substringBefore` already returns the original string when
   the delimiter is absent, so the ternary collapses to one expression
3. Remove `split`, `dropLastWhile`, and `toTypedArray` from any imports that are no longer used
acceptance: `./gradlew testDefaultDebugUnitTest` green; exam submission parent-ID resolves
correctly for both plain IDs and `userId@timestamp` formatted IDs
size budget: ~4 changed lines, 1 file
out of scope: no changes to exam submission flow or score calculation

---

### 10. Remove redundant `toList()` in CollectionsFragment parent-click handler (roadmap 7)

context: `CollectionsFragment.kt` line 131 calls `adapter.submitList(currentTagDataList.toList())`
where `currentTagDataList` is already a `MutableList<TagData.Parent>`. `submitList` takes a `List`
and internally makes its own snapshot, so passing a second copy is wasteful on every parent-tag
expand/collapse.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt (line 131).
Do NOT touch `CollectionsViewModel` or any repository.
steps:
1. Line 131: change `adapter.submitList(currentTagDataList.toList())` → `adapter.submitList(currentTagDataList)`
2. Verify `submitList` is not called elsewhere with a `.toList()` that wraps a `List`-typed variable
acceptance: `./gradlew testDefaultDebugUnitTest` green; collections tree still expands and collapses
the correct tag groups on click
size budget: ~1 changed line, 1 file
out of scope: no changes to collection-tree data building logic
