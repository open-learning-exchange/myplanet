Date: 2024-05-24 · Base commit: 89fd72c251df68ed01094091d4de7ba7a2571ebe · Checked PRs: 16293, 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848

### 1. replace toList() with size verification for tags mapping (roadmap 7)
context: CollectionsViewModel.kt:48 calls `val list = tagsWithChildren.keys.toList()`, which allocates an unnecessary list of keys just to check if the map is empty. A direct `.isEmpty()` call on the map handles this without memory allocations.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsViewModel.kt (line 48). do NOT touch other UI files.
steps: 1. replace `tagsWithChildren.keys.toList()` with `.size` or direct `.isEmpty()` checks for determining success vs empty state. 2. update `list` assignment or logic 3. run the tests
acceptance: ./gradlew testDefaultDebugUnitTest; collection view correctly loads empty or populated tags.
size budget: ~4 changed lines, 1 file
out of scope: modifying `TagsRepository`

---
### 2. optimize `Constants.LABEL_VALUE_TO_NAME` map initialization (roadmap 7)
context: `Constants.LABEL_VALUE_TO_NAME` (line 39) iterates over `LABELS.entries` mapping it to a list of pairs using `.associate` which creates a list and then a map. We should directly create a map with a loop or `associate` directly from the `LABELS` map to avoid the intermediate list allocation.
files: app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt (line 39). do NOT touch other Constants fields.
steps: 1. modify `by lazy` block to directly iterate `LABELS` mapping entries to a mutable map and returning it. 2. check compilation
acceptance: ./gradlew testDefaultDebugUnitTest; constant map remains functionally equivalent but avoids intermediate tuple allocations
size budget: ~5 changed lines, 1 file
out of scope: adding new labels to the Constants file

---
### 3. remove intermediate list creation in getMostOpenedResource (roadmap 1+7)
context: ActivitiesRepositoryImpl.kt:183 uses `.filterValues { it.second != null }` which creates intermediate lists and maps. Iterate and filter directly over `resourceCounts` to find the maximum value directly.
files: app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt (line 183).
steps: 1. replace `.filterValues` and `.maxByOrNull` with a direct loop tracking the maximum entry. 2. confirm function return type
acceptance: ./gradlew testDefaultDebugUnitTest; most opened resource correctly returned with zero intermediate allocations.
size budget: ~15 changed lines, 1 file
out of scope: changing database schemas for `resourceActivityDao`

---
### 4. optimize `getLinkIdsForTagNames` allocations (roadmap 1+7)
context: TagsRepositoryImpl.kt:58 uses `tagDao.getByNames(tagNames).map { it.id }` which allocates an intermediate list before fetching from the database on line 62 with `.mapNotNull`. This should use `.mapNotNullTo` or a loop.
files: app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt (line 58).
steps: 1. update the list mapping logic to skip the intermediate list allocations. 2. run test suites
acceptance: ./gradlew testDefaultDebugUnitTest; tag linking behaves the same as previously with reduced heap footprint
size budget: ~5 changed lines, 1 file
out of scope: modifying `TagDao` functions

---
### 5. eliminate redundant pairwise allocations in updateMyLifeListOrder (roadmap 1+7)
context: LifeRepositoryImpl.kt:43 calls `list.mapIndexed { index, item -> item._id to index }.toMap()`, creating an intermediate list of `Pair`s. Replace with a standard loop populating a map, or `.associateByTo`.
files: app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt (line 43)
steps: 1. create an empty map. 2. use `list.forEachIndexed` to set map entries. 3. check compiler errors
acceptance: ./gradlew testDefaultDebugUnitTest; life list order is correctly persisted on rearrange
size budget: ~6 changed lines, 1 file
out of scope: touching any other functions in `LifeRepositoryImpl`

---
### 6. prevent redundant values list copy in `getUniqueSurveys` (roadmap 1+7)
context: SubmissionsRepositoryImpl.kt:123 returns `uniqueSurveys.values.toList()`. `uniqueSurveys` is already a linked map. Iterating `values` directly or returning the mutable collection directly avoids allocating a new standard `ArrayList`.
files: app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt (line 123)
steps: 1. change return signature or cast correctly if possible to avoid `.toList()` 2. verify compilation
acceptance: ./gradlew testDefaultDebugUnitTest; survey processing screens successfully retrieve distinct surveys
size budget: ~5 changed lines, 1 file
out of scope: modifying `Submission` model structure

---
### 7. replace intermediate pair generation with direct loop mapping (roadmap 1+7)
context: FeedbackRepositoryImpl.kt:125 performs `.map { it to JsonUtils...}` into a List of Pairs, which is then remapped using `.associateBy { it.id }` and finally `.map { ... }`. Converting this chain to a single loop mapping to a LinkedHashMap avoids multiple intermediate Lists.
files: app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt (line 125)
steps: 1. instantiate empty maps. 2. iterate through `jsonObjects`, caching ID manually and inserting directly into `mappedList` maps 3. verify test compatibility
acceptance: ./gradlew testDefaultDebugUnitTest; feedback synchronization processes identical payload successfully
size budget: ~10 changed lines, 1 file
out of scope: altering `Feedback` mapping utilities

---
### 8. prevent remapping collection when mapping `courses` (roadmap 1+7)
context: CoursesRepositoryImpl.kt:866 maps course list IDs by remapping values again using `.mapValues { entry -> entry.value.map { it } }`. This copies the map elements to a new array unnecessarily since it could iterate or just accept the resulting grouped layout if not explicitly needed.
files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt (line 866)
steps: 1. remove `.mapValues { entry -> entry.value.map { it } }` logic. 2. ensure proper variable references
acceptance: ./gradlew testDefaultDebugUnitTest; course maps continue to process correctly
size budget: ~3 changed lines, 1 file
out of scope: refactoring `courseStepDao` usage

---
### 9. replace intermediate pair mappings in `buildNewsFromJson` logic (roadmap 1+7)
context: VoicesRepositoryImpl.kt:391 extracts an ID and builds pairs, followed immediately by filtering then remapping the pairs into a list via `.map`. Changing this to sequence operations or a single loop will drastically reduce temporary array GC.
files: app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt (line 391)
steps: 1. implement a loop for `docs` or use `.asSequence()`. 2. accumulate the outputs into targeted lists instead of consecutive `.map` chains
acceptance: ./gradlew testDefaultDebugUnitTest; news parsing constructs valid `News` model arrays
size budget: ~10 changed lines, 1 file
out of scope: altering `newsDao` query

---
### 10. remove redundant collection parsing overhead (roadmap 1+7)
context: ProgressRepositoryImpl.kt:147 uses `.map { it }` which returns a shallow list copy from a list type instead of just assigning the underlying array `answerDao.getBySubmissionIds(submissionIds)`.
files: app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt (line 147)
steps: 1. drop the trailing `.map { it }`. 2. verify test results
acceptance: ./gradlew testDefaultDebugUnitTest; answers lists appropriately process answers bounds
size budget: ~2 changed lines, 1 file
out of scope: removing valid mapping steps downstream
