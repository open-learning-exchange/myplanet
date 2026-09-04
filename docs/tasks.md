date: 2024-05-24
base commit: HEAD
open PRs checked: could not check PRs due to gh cli failure

### 1. collapse list filtering into single-pass mapNotNull (roadmap 7)
context: CoursesRepositoryImpl.kt:830 currently chains `.map { ... }.filter { ... }`.
This approach creates an intermediate list of strings, which is unnecessary and
increases garbage collection pressure, particularly on critical paths.
By replacing this chained sequence with a single `.mapNotNull { ...takeIf { it.isNotBlank() } }` call,
we can effectively collapse the two passes into one.
This avoids the intermediate list allocation entirely and improves runtime performance.
files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt (line 830).
Ensure you do NOT touch TeamsRepositoryImpl — open PRs own it.
Other files in the repository must also remain unaffected.
steps: 1. Locate line 830 in CoursesRepositoryImpl.kt.
2. Replace the chained `.map { JsonUtils.getString("_id", it.doc) }.filter { it.isNotBlank() }` call.
3. Use `.mapNotNull { JsonUtils.getString("_id", it.doc).takeIf { it.isNotBlank() } }` instead.
4. Verify that no other lines are unintentionally modified.
5. Run the standard unit test suite to verify no regressions were introduced.
acceptance: ./gradlew testDefaultDebugUnitTest green;
The course processing logic proceeds without any issues or missing data.
The UI still correctly displays course progress and information.
size budget: ~2 changed lines,
1 file touched in total.
out of scope: Do not introduce any functional or logic changes.
This is strictly a micro-optimization task to remove intermediate allocations.

---

### 2. optimize collection distincting with mapNotNullTo for Progress docIds (roadmap 7)
context: ProgressRepositoryImpl.kt:270 chains `.map { ... }.filter { ... }.distinct()` for extracting docIds.
This results in multiple intermediate list allocations before the final deduplicated set is returned.
Using Kotlin's `.mapNotNullTo(LinkedHashSet()) { ... }` allows us to filter, transform,
and deduplicate elements into an insertion-ordered set in a single pass.
This eliminates the overhead of intermediate arrays and improves execution speed.
files: app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt (line 270).
All other repository files are to be left as they are.
Do not alter any DAO interfaces or query logic.
steps: 1. Open ProgressRepositoryImpl.kt and go to line 270.
2. Find the expression assigning to `docIds` (and similarly for courseIds and userIds directly below it if applicable).
3. Replace `.map { JsonUtils.getString("_id", it) }.filter { it.isNotEmpty() }.distinct()`.
4. Use `.mapNotNullTo(LinkedHashSet()) { JsonUtils.getString("_id", it).takeIf { it.isNotEmpty() } }.toList()`.
5. Verify the tests pass and the progress doc processing functions as expected.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Progress doc processing functions efficiently without dropping entries.
The user does not experience any regression in progress synchronization.
size budget: ~6 changed lines,
1 file modified.
out of scope: No logic changes are permitted.
This task is exclusively for collection operation micro-optimization.

---

### 3. collapse list mapping and filtering in VoicesRepository (roadmap 7)
context: VoicesRepositoryImpl.kt:362 chains `.map { it.second }.filter { it.isNotEmpty() }`.
This involves two collection passes, generating a temporary list after mapping.
Switching this to `.mapNotNull { it.second.takeIf { it.isNotEmpty() } }` is a quick win.
It optimizes collection manipulation by discarding the intermediate list.
Such cleanups are essential for moving towards a performant Kotlin core.
files: app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt (line 362).
Do not make any changes to the Teams voices fragment.
Ensure the voice recording components are ignored.
steps: 1. Find line 362 in VoicesRepositoryImpl.kt.
2. Review the `underscoreIds` assignment.
3. Replace `.map { it.second }.filter { it.isNotEmpty() }`.
4. Use `.mapNotNull { it.second.takeIf { it.isNotEmpty() } }`.
5. Ensure that the codebase compiles and tests pass successfully.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Voice processing proceeds efficiently and without errors.
The UI continues to correctly display and play voice items.
size budget: ~2 changed lines,
1 file affected.
out of scope: No changes to how voice files are downloaded or stored.
Just a collection transformation refactor.

---

### 4. collapse mapNotNull and distinct in SubmissionsRepository pendingSurveys (roadmap 7)
context: SubmissionsRepositoryImpl.kt:109 chains `.mapNotNull { ... }.distinct()`.
Although `mapNotNull` removes nulls, `distinct()` creates another list allocation to guarantee uniqueness.
This can be optimized directly into `.mapNotNullTo(LinkedHashSet()) { ... }.toList()`.
Using a LinkedHashSet deduplicates elements during insertion while preserving order.
This avoids the final list-to-list distinct operation entirely.
files: app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt (line 109).
Leave other lines in the repository untouched.
Do not alter the survey submission models.
steps: 1. Open SubmissionsRepositoryImpl.kt at line 109.
2. Locate the `examIds` calculation for pending surveys.
3. Replace `.mapNotNull { it.examIdFromParentId() }.distinct()`.
4. Use `.mapNotNullTo(LinkedHashSet()) { it.examIdFromParentId() }.toList()`.
5. Run tests to confirm survey logic is unaffected.
acceptance: ./gradlew testDefaultDebugUnitTest green;
The examIds collection functions efficiently without dropping entries.
Pending surveys are accurately fetched and synced.
size budget: ~2 changed lines,
1 file.
out of scope: Do not rewrite the examIdFromParentId extension function.
No logic changes related to survey completion states.

---

### 5. replace view-size calculation with list size check in ExamTakingFragment (roadmap 7)
context: ExamTakingFragment.kt:118 checks `(questions?.size ?: 0) > 0`.
This is a non-idiomatic way of verifying if a list has elements in Kotlin.
A more readable and slightly more optimal approach is `!questions.isNullOrEmpty()`.
This removes the need for Elvis operators and explicit integer comparisons.
Improving such snippets enhances code health and maintainability across the UI layer.
files: app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt (line 118).
Do not make modifications to the ExamTakingFragment ViewModel.
Ensure other list size checks in the file remain for now if out of scope.
steps: 1. Navigate to line 118 in ExamTakingFragment.kt.
2. Look for the conditional statement checking if questions exist.
3. Replace the condition `if ((questions?.size ?: 0) > 0)`.
4. Change it to `if (!questions.isNullOrEmpty())`.
5. Re-run UI or unit tests to ensure nothing broke.
acceptance: ./gradlew testDefaultDebugUnitTest green;
The exam screen still correctly displays questions when they exist.
Empty states are handled correctly without crashes.
size budget: ~2 changed lines,
1 file.
out of scope: Do not rewrite the entire ExamTakingFragment.
Refrain from any UI or Jetpack Compose migrations in this task.

---

### 6. replace selectedItems size check in ResourcesFragment (roadmap 7)
context: ResourcesFragment.kt:409 checks `if ((selectedItems?.size ?: 0) > 0)`.
Similar to other parts of the codebase, this pattern is overly verbose and suboptimal.
Using Kotlin's standard library function `!selectedItems.isNullOrEmpty()` is better.
It conveys intent more clearly and avoids the primitive integer wrapper allocations.
This falls under the scope of micro-optimizations that improve overall code health.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt (line 409).
Do not touch BaseResourceFragment or its subclasses.
Only target this exact line within ResourcesFragment.
steps: 1. Open ResourcesFragment.kt and find line 409.
2. Locate the size condition for `selectedItems`.
3. Replace `if ((selectedItems?.size ?: 0) > 0)`.
4. Use `if (!selectedItems.isNullOrEmpty())`.
5. Validate with tests and ensure it compiles.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Resource selection and downloading still works flawlessly.
Button states update properly based on the selection.
size budget: ~2 changed lines,
1 file touched.
out of scope: Do not refactor the adapter logic for selection.
No logical changes to how resources are managed.

---

### 7. replace selectedItems size check in CoursesFragment (roadmap 7)
context: CoursesFragment.kt:268 checks `if ((selectedItems?.size ?: 0) > 0)`.
This evaluates a nullable list size using an Elvis operator instead of idiomatic functions.
Refactoring this to `if (!selectedItems.isNullOrEmpty())` improves both readability and execution.
It aligns with Kotlin's built-in collection checks for nullability and emptiness.
These quick wins are necessary before migrating fragments to modern Compose paradigms.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt (line 268).
Do not modify the CoursesViewModel or Adapter.
Only apply changes to the aforementioned fragment line.
steps: 1. Locate line 268 in CoursesFragment.kt.
2. Find the if statement evaluating `selectedItems`.
3. Change `if ((selectedItems?.size ?: 0) > 0)`.
4. Use `if (!selectedItems.isNullOrEmpty())`.
5. Ensure all tests continue to pass.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Course selection still functions as expected by the user.
Bulk actions depend correctly on the active selection state.
size budget: ~2 changed lines,
1 file.
out of scope: No changes to how courses are displayed or filtered.
Do not introduce Compose implementations here.

---

### 8. collapse mapNotNull and distinct in TagsRepository (roadmap 7)
context: TagsRepositoryImpl.kt:76 chains `.mapNotNull { ... }.distinct()`.
While `mapNotNull` processes the list by filtering nulls, `distinct()` requires allocating an additional list under the hood.
Applying `.mapNotNullTo(LinkedHashSet())` performs the deduplication in a single pass during element transformation.
This ensures minimal memory overhead and better performance in repositories.
By establishing this pattern, we ensure consistent and performant data processing across repositories.
files: app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt (line 76).
Ensure you only update TagsRepositoryImpl.
Avoid changing any business logic related to tag linkages.
steps: 1. Navigate to line 76 in TagsRepositoryImpl.kt.
2. Locate the calculation of `allTagIds`.
3. Replace `.mapNotNull { it.tagId }.distinct()`.
4. Use `.mapNotNullTo(LinkedHashSet()) { it.tagId }.toList()`.
5. Confirm changes compile and the test suite passes.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Tag IDs are accurately retrieved without duplicates.
System correctly filters by tags on relevant screens.
size budget: ~2 changed lines,
1 file.
out of scope: No structural changes to how tags are fetched from the database.
Focus entirely on collection optimization.

---

### 9. collapse filter and mapNotNull in ResourcesRepository (roadmap 7)
context: ResourcesRepositoryImpl.kt:466 currently uses `.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }`.
Chaining `filter` before `mapNotNull` causes two distinct collection passes and creates an intermediate list.
This can be seamlessly merged into a single `.mapNotNull` call where the filtering condition returns null when skipped.
Such micro-optimizations remove unnecessary allocations on hot paths.
It brings us closer to a zero-waste Kotlin core.
files: app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt (line 466).
Do not modify other functions inside ResourcesRepositoryImpl.
Make sure not to affect offline resource downloading functionality.
steps: 1. Find line 466 in ResourcesRepositoryImpl.kt.
2. Locate the extraction logic for `urls`.
3. Replace `.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }`.
4. Use `.mapNotNull { if (!it.isResourceOffline()) it.resourceRemoteAddress else null }`.
5. Execute test suites to ensure correctness.
acceptance: ./gradlew testDefaultDebugUnitTest green;
The correct set of remote addresses is extracted for downloading.
Offline resources are still accurately excluded.
size budget: ~2 changed lines,
1 file touched.
out of scope: No alterations to resource offline checking logic.
Do not introduce any coroutine or structural refactors.

---

### 10. collapse mapNotNull and filter in BellDashboardViewModel (roadmap 7)
context: BellDashboardViewModel.kt:79 chains `.mapNotNull { submissionsById[it] }.filter { it.status == "pending" }`.
This double-pass operation can be executed in a single loop using `.mapNotNull` with a `takeIf` block.
Skipping intermediate collection allocations in ViewModels reduces UI stutter and memory pressure.
This keeps the state derivation fast and lightweight.
Such optimizations are especially crucial as we incrementally adopt Compose.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt (line 79).
Only make changes to the pending surveys computation.
Do not touch any other properties in BellDashboardViewModel.
steps: 1. Open BellDashboardViewModel.kt and go to line 79.
2. Spot the `pendingSurveys` initialization.
3. Replace `.mapNotNull { submissionsById[it] }.filter { it.status == "pending" }`.
4. Change it to `.mapNotNull { submissionsById[it]?.takeIf { it.status == "pending" } }`.
5. Run relevant unit tests to guarantee no breaking changes.
acceptance: ./gradlew testDefaultDebugUnitTest green;
Pending surveys are displayed accurately on the dashboard bell icon.
Other notification features remain completely functional.
size budget: ~2 changed lines,
1 file.
out of scope: No architectural changes to the ViewModel.
Do not introduce new state flows or livedata variables.

---
