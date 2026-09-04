2024-05-24 · HEAD · could not check open PRs

### 1. replace team-course list load with the existing count query (roadmap 1+7)
context: TeamCoursesFragment.kt:57 checks state.courses.size to decide if empty text should show. Loading full course objects into the Fragment just for an empty check wastes memory when the repository has team-course counts.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt (line 57). do NOT touch TeamCoursesViewModel — focus on the UI side to use a count variable from ViewModel state instead of full list.
steps: 1. update the UI state to expose a `coursesCount` 2. change showNoData call to use the count 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 2. swap full team-task list load for an existing task-count query (roadmap 1+7)
context: TeamsTasksFragment.kt:339 uses taskList.size to show empty state, which fetches complete task objects into memory.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt (line 339). do NOT touch TeamsTasksViewModel.
steps: 1. use a count property from ViewModel state 2. update showNoData to use count 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 3. use repository count for team discussions empty state (roadmap 1+7)
context: TeamsVoicesFragment.kt:262 checks realmNewsList?.filterNotNull()?.size to show empty state, retrieving full news objects unnecessarily.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt (line 262). do NOT touch TeamsVoicesViewModel.
steps: 1. introduce a count state in ViewModel 2. update showNoData to use the count 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 4. optimize courses list size check for empty state (roadmap 1+7)
context: CoursesFragment.kt:151 and 200 use cachedState.courses.size and state.courses.size, loading full courses into memory when only the count is needed for the empty state message.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt (lines 151, 200). do NOT touch CoursesViewModel.
steps: 1. add coursesCount to state 2. use it in showNoData 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 5. replace resources list load with count for empty state (roadmap 1+7)
context: ResourcesFragment.kt:236 and 476 call filteredList.size for the empty message, loading full objects instead of using a count query.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt (lines 236, 476). do NOT touch ResourcesViewModel.
steps: 1. add resourcesCount to state 2. update showNoData calls 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 6. use member-count for requests header instead of list size (roadmap 1+7)
context: MembersFragment.kt:86 formats the join requests header using state.members.size, loading all member objects to build a string.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt (line 86). do NOT touch MembersViewModel.
steps: 1. add requestsCount to state 2. update the header text setting 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; header still shows correct count
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 7. replace team-members empty state size check with count (roadmap 1+7)
context: MembersFragment.kt:102 calls members.size for the empty state, fetching full member records instead of querying the count.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt (line 102). do NOT touch MembersViewModel.
steps: 1. add membersCount to state 2. update showNoData call 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 8. optimize requests fragment empty state with count (roadmap 1+7)
context: RequestsFragment.kt:53 uses uiState.members.size to display the empty state, loading full data objects unnecessaily.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt (line 53). do NOT touch RequestsViewModel.
steps: 1. add requestsCount to state 2. update showNoData call 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 9. replace voices empty state list check with count (roadmap 1+7)
context: VoicesFragment.kt:186 checks sortedList.size, which means all voice/discussion objects are loaded just to decide if the screen is empty.
files: app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt (line 186). do NOT touch VoicesViewModel.
steps: 1. add voicesCount to state 2. update showNoData call 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes

### 10. use enterprise-reports count query for empty state (roadmap 1+7)
context: EnterprisesReportsFragment.kt:396 uses reports.size for the empty message, pulling all reports into memory instead of just counting them.
files: app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt (line 396). do NOT touch EnterprisesViewModel.
steps: 1. add reportsCount to state 2. update showNoData call 3. run tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; empty state still triggers properly
size budget: ~15 changed lines, 2 files
out of scope: DAO layer changes
