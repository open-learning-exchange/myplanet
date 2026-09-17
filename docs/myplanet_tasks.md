2026-09-17 · c33390b88 · could not check open PRs

### 1. replace list-size load with the existing count query in RequestsViewModel (roadmap 1+7)
context: `RequestsViewModel.kt` calls `teamsRepository.getRequestedMembers(teamId)` which loads a full list of `UserEntity` objects into memory, but `RequestsFragment.kt:53` checks `uiState.members.size` to show `tvNodata`. We should use an optimized COUNT query.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt, app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt (do NOT touch TeamsRepositoryImpl — open PRs own it).
steps: 1. Add `suspend fun getRequestedMemberCount(teamId: String): Int` to `TeamsMembersRepository`. 2. Expose a `requestedMemberCount` property in `RequestsUiState`. 3. Update `RequestsViewModel.fetchMembers` to fetch this count using `getRequestedMemberCount`. 4. Use `uiState.requestedMemberCount` in `RequestsFragment` for the `showNoData` call instead of `.size`.
acceptance: ./gradlew testDefaultDebugUnitTest green; requests screen still shows the correct count of pending requests and empty states.
size budget: ~20 changed lines, 3 files
out_of_scope: No changes to `TeamsRepositoryImpl` or DAO.
---
### 2. inject ServerReachabilityProvider to remove MainApplication dependency in BellDashboardViewModel (roadmap 4)
context: `BellDashboardViewModel.kt:137` calls `MainApplication.isServerReachable(serverUrl)`, keeping a static dependency on the global application instance and preventing proper offline testing.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt
steps: 1. Inject `ServerReachabilityProvider` into `BellDashboardViewModel`. 2. Replace the static `MainApplication.isServerReachable` call with `serverReachabilityProvider.isServerReachable`. 3. Remove the unused `MainApplication` import.
acceptance: ./gradlew testDefaultDebugUnitTest green; network reachability checks continue to function on the bell dashboard.
size budget: ~10 changed lines, 1 file
out_of_scope: No changes to MainApplication itself.
---
### 3. inject ServerReachabilityProvider to remove MainApplication dependency in ChatRepositoryImpl (roadmap 4+9)
context: `ChatRepositoryImpl.kt:41` calls `MainApplication.isServerReachable(url)`, introducing an Android static context dependency into the repository layer.
files: app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt
steps: 1. Inject `ServerReachabilityProvider` into `ChatRepositoryImpl`. 2. Replace the `MainApplication.isServerReachable(url)` call with `serverReachabilityProvider.isServerReachable(url)`. 3. Update any missing imports and remove `MainApplication`.
acceptance: ./gradlew testDefaultDebugUnitTest green; chat still checks network correctly.
size budget: ~10 changed lines, 1 file
out_of_scope: Do not touch TeamsRepositoryImpl.
---
### 4. remove static MainApplication.isCollectionSwitchOn in CollectionsFragment (roadmap 4+10)
context: `CollectionsFragment.kt` uses a global static `MainApplication.isCollectionSwitchOn` to hold UI state, violating ViewModel state boundaries and breaking if the process is killed.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt, app/src/main/java/org/ole/planet/myplanet/MainApplication.kt
steps: 1. Remove `isCollectionSwitchOn` from `MainApplication.kt`. 2. Add a `private var isCollectionSwitchOn: Boolean = false` directly into `CollectionsFragment.kt` (or create/use a ViewModel if one already exists for this state). 3. Replace all usages of `MainApplication.isCollectionSwitchOn` with the local field.
acceptance: ./gradlew testDefaultDebugUnitTest green; collections filter selection works without crashing.
size budget: ~15 changed lines, 2 files
out_of_scope: No ViewModel creation if one doesn't exist; just move it to fragment scope.
---
### 5. remove static MainApplication.showDownload in TeamDetailFragment (roadmap 4+10)
context: `TeamDetailFragment.kt` uses `MainApplication.showDownload` (a static variable) to coordinate page switches, leaking UI state into the application instance.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt, app/src/main/java/org/ole/planet/myplanet/MainApplication.kt
steps: 1. Remove `showDownload` from `MainApplication.kt`. 2. Move `showDownload` into `TeamDetailFragment.kt` as a private property (e.g. `private var showDownload = false`). 3. Replace all accesses to `MainApplication.showDownload` with the local property.
acceptance: ./gradlew testDefaultDebugUnitTest green; download button still works in teams.
size budget: ~10 changed lines, 2 files
out_of_scope: Do not rewrite the ViewPager logic.
---
### 6. decouple TeamPagerAdapter and TeamDetailFragment from MainApplication.listener (roadmap 4+6)
context: `TeamPagerAdapter` and `TeamDetailFragment` use a global `MainApplication.listener` to communicate child fragment events up to the parent. This static leak violates Android component lifecycles and breaks if multiple team pages open.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt, app/src/main/java/org/ole/planet/myplanet/MainApplication.kt
steps: 1. Remove `var listener: OnTeamPageListener?` from `MainApplication.kt`. 2. Update `TeamPagerAdapter` to not set `MainApplication.listener`. 3. In `TeamDetailFragment`, when `btnAddDoc` is clicked, find the currently active fragment from the ViewPager and cast it to `OnTeamPageListener` instead of relying on the static field.
acceptance: ./gradlew testDefaultDebugUnitTest green; adding a document from a team page works without crashing.
size budget: ~20 changed lines, 3 files
out_of_scope: No new interfaces; use existing `OnTeamPageListener`.
---
### 7. replace members list load with count query in MembersFragment (roadmap 1+7)
context: `MembersFragment.kt:96` checks `state.members.size` to display empty data via `showNoData`. To improve performance, we shouldn't rely on the list size for UI state if we can avoid it.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt, app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt (do NOT touch TeamsRepositoryImpl)
steps: 1. Ensure `TeamsMembersRepository` exposes `suspend fun getJoinedMemberCount(teamId: String): Int` (already exists). 2. Add `joinedMemberCount` to `MembersUiState` in `RequestsViewModel`. 3. Update `loadJoinedMembers` to query and set this count. 4. In `MembersFragment`, use `state.joinedMemberCount` instead of `state.members.size` for `showNoData`.
acceptance: ./gradlew testDefaultDebugUnitTest green; members tab correctly shows no-data message when empty.
size budget: ~15 changed lines, 3 files
out_of_scope: DAO modifications.
---
### 8. replace list-size load with the existing count query in NotificationsViewModel (roadmap 1+7)
context: `NotificationsViewModel.kt:48` calls `.map { it.size }` on a flow of full notification list just to get the count, unnecessarily parsing the entire list.
files: app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt
steps: 1. Modify the repository to expose a flow that directly queries the count of notifications instead of returning a flow of the list. 2. Update `NotificationsViewModel` to collect this count flow. 3. Ensure UI still properly updates with the notification count.
acceptance: ./gradlew testDefaultDebugUnitTest green; notification counts correctly appear.
size budget: ~15 changed lines, 2 files
out_of_scope: Do not rewrite the main notification UI logic.
---
### 9. remove static context from UserRepositoryImpl (roadmap 4+9)
context: `UserRepositoryImpl.kt:527` uses `MainApplication.context` for `VersionUtils` and `NetworkUtils`, leaking the static application context into the repository layer. It should use an injected context.
files: app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt
steps: 1. Inject `@ApplicationContext private val context: Context` into the `UserRepositoryImpl` constructor (if not already there). 2. Replace `MainApplication.context` with the injected `context` in lines 527 and 528. 3. Remove `MainApplication` import.
acceptance: ./gradlew testDefaultDebugUnitTest green; user registration payloads still include correct device info.
size budget: ~10 changed lines, 1 file
out_of_scope: Refactoring NetworkUtils or VersionUtils directly.
---
### 10. remove static MainApplication.applicationScope from NotificationActionReceiver (roadmap 4+5)
context: `NotificationActionReceiver.kt:32` uses the global `MainApplication.applicationScope.launch` to start a coroutine in a BroadcastReceiver, which breaks testability and lifecycle isolation.
files: app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt
steps: 1. Create a local scope in `NotificationActionReceiver` or inject a DispatcherProvider. 2. Launch the coroutine using `goAsync()` context logic. 3. Remove `MainApplication` import.
acceptance: ./gradlew testDefaultDebugUnitTest green; notification clicks still register properly.
size budget: ~15 changed lines, 1 file
out_of_scope: Refactoring other broadcast receivers.
