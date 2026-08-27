date: 2026-08-27
base commit: 45bac8d0050286289dbb8fa9680864a16758be5f
open PRs checked: could not check open PRs
---
### 1. replace redundant `.size` calculation for total course items in CoursesFragment (roadmap 7)
context: `CoursesFragment.kt:475` uses `myCourses.size` on the full list of courses without considering any applied filters, causing incorrect selection counts in the UI. When dealing with recycled views and dynamically populated lists, tracking `.size` on a separate collection introduces inconsistencies. We should use the adapter's built-in item count directly.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt (line 475)
steps:
  1. change `myCourses.size` to `adapterCourses.itemCount` which reflects the currently filtered list
  2. remove the `myCourses` variable if it is no longer used elsewhere in the file
  3. run unit tests to verify behavior
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; selecting all courses in the UI shows the correct count
size budget: ~2 changed lines, 1 file
out of scope: no layout changes, no logic changes in adapter
---
### 2. optimize CoursesAdapter selection logic to avoid redundant filtering (roadmap 7)
context: `CoursesAdapter.kt:196` filters the list every time `areAllSelected()` is called to compute `selectableCourses`. The list `selectableCourses` is also computed redundantly in `selectAllItems` at line 205. This causes unnecessary list allocations on the heap every time the user taps the selection checkbox.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt (lines 196, 205)
steps:
  1. update `areAllSelected` to avoid filtering when `currentList` is empty
  2. count `selectableCourses` size directly via `.count { ... }` instead of `.filter { ... }.size`
  3. update `selectAllItems` to add items directly to `selectedItems` via `.filterTo(selectedItems) { ... }`
  4. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; selecting all courses works as expected
size budget: ~5 changed lines, 1 file
out of scope: no logic changes to the underlying list tracking
---
### 3. optimize storage breakdown size array initialization (roadmap 7)
context: `StorageBreakdownFragment.kt:230` uses a loop to accumulate directory sizes into standard lists/maps, then converts them to arrays. This requires resizing the array dynamically and allocating a new primitive array, which slows down the UI thread during a heavy I/O operation.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt (line 230)
steps:
  1. initialize `LongArray` and `IntArray` directly with `categories.size`
  2. iterate over categories by index and accumulate sizes and counts directly into arrays
  3. remove intermediate lists/maps
  4. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; storage breakdown UI still accurate
size budget: ~5 changed lines, 1 file
out of scope: no changes to actual directory iteration logic
---
### 4. replace redundant loaded count calculation in ChatViewModel (roadmap 7)
context: `ChatViewModel.kt:204` calculates `minOf(PAGE_SIZE, parsedConversations.size)`, creating a list copy just to get its size. `parsedConversations` maps 1:1 to `conversations`, meaning we allocate a new list entirely to get a size we already know. This wastes CPU and memory during chat initialization.
files: app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt (line 204)
steps:
  1. update the check to use the original conversations list size directly
  2. replace `parsedConversations.size` with `conversations.size`
  3. run unit tests to ensure bounds checking remains correct
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; chat history pagination still correct
size budget: ~2 changed lines, 1 file
out of scope: no layout or network changes
---
### 5. avoid counting items twice in members list empty state check (roadmap 7)
context: `MembersFragment.kt:107` checks `members.size` to show empty state, though the adapter also checks it. In Kotlin and Android development, relying on parallel size checks can cause empty states to show incorrectly if the adapter modifies its local list asynchronously.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt (line 107)
steps:
  1. move the empty state visibility toggle directly into the adapter data update callback
  2. utilize the existing list reference passed to the adapter
  3. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; empty state still toggles properly
size budget: ~3 changed lines, 1 file
out of scope: no layout changes
---
### 6. streamline list size string formatting in DictionaryActivity (roadmap 7)
context: `DictionaryActivity.kt:66` uses `getString(R.string.list_size, count)` redundantly in multiple places. Extracting string resources during initialization and repeatedly inside loops or state updates can lead to excessive view invalidations.
files: app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt (line 66)
steps:
  1. extract the string formatting to a single update method called when count changes
  2. apply this method everywhere the dictionary size changes
  3. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; dictionary list size still formatted correctly
size budget: ~4 changed lines, 1 file
out of scope: no layout changes
---
### 7. remove redundant string formatting in CourseStepFragment (roadmap 7)
context: `CourseStepFragment.kt:121` uses `getString(R.string.resources_size, resources.size)` repeatedly without checking if resources are empty. We should avoid evaluating and setting text on a button that will end up being hidden anyway, as it causes unnecessary layout passes.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt (line 121)
steps:
  1. check if `resources.isNotEmpty()` before formatting the string
  2. set text and visibility only if resources are present
  3. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; resources button behaves properly
size budget: ~4 changed lines, 1 file
out of scope: no logic changes for markdown parsing
---
### 8. replace member list size check with member count lookup (roadmap 7)
context: `TeamsTasksFragment.kt:128` uses `teamsTasksViewModel.getJoinedMembers(teamId)` to get the member list and check if it's empty, rather than querying the count directly. Loading full user models just to check if the team is empty is a heavy DB hit for a simple assignment dialog.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt (line 128)
steps:
  1. call `teamsTasksViewModel.getJoinedMemberCount(teamId)`
  2. use this count to short-circuit the full list fetch and filtering if count is 0
  3. if count > 0, proceed with fetching the list
  4. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; team task assignment dialog still opens when members exist
size budget: ~6 changed lines, 1 file
out of scope: no UI redesign for the dialog itself
---
### 9. implement lightweight member count lookup for TeamsTasksViewModel (roadmap 7)
context: `TeamsTasksViewModel.kt` currently only exposes `getJoinedMembers(teamId)`, causing Fragments to fetch full entities for simple presence checks. Adding a count property directly to the view model keeps database queries decoupled from the UI.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt
steps:
  1. add a new suspending function `getJoinedMemberCount(teamId: String)`
  2. delegate the call to `teamsRepository.getJoinedMemberCount(teamId)`
  3. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; function returns the correct count integer
size budget: ~4 changed lines, 1 file
out of scope: no changes to the repository logic
---
### 10. remove redundant size property mapping in NotificationsViewModel (roadmap 7)
context: `NotificationsViewModel.kt:47` maps the flow to `.map { it.size }` continuously, triggering redundant emits when content changes but size doesn't. If the list is updated but the size remains the same, downstream observers are needlessly notified, forcing UI redraws.
files: app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt (line 47)
steps:
  1. add `.distinctUntilChanged()` after the `.map { it.size }` flow mapping
  2. verify downstream observers only fire on actual count changes
  3. run unit tests
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; notification count badge updates correctly without redundant redraws
size budget: ~2 changed lines, 1 file
out of scope: no changes to the notification repository logic
