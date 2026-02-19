# Kotlin Deprecation Warning Fix Tasks

This document lists all deprecation and compiler warnings from the release build, grouped by root cause, with actionable fix tasks.

---

### 1. Replace deprecated `userModel` property in BaseResourceFragment

The `profileDbHandler.userModel` property is deprecated in favor of the `getUserModel()` suspend function. Three call sites in `BaseResourceFragment` access this deprecated property to obtain the user ID for shelf and survey operations.

:codex-file-citation[codex-file-citation]{line_range_start=239 line_range_end=239 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L239-L239"}
:codex-file-citation[codex-file-citation]{line_range_start=374 line_range_end=374 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L374-L374"}
:codex-file-citation[codex-file-citation]{line_range_start=418 line_range_end=418 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L418-L418"}

:::task-stub{title="Replace deprecated userModel in BaseResourceFragment"}
1. Convert `showPendingSurveyDialog()` to use `profileDbHandler.getUserModel()` suspend function at line 239
2. Convert `removeFromShelf()` to use the suspend version at line 374, wrapping in a coroutine if needed
3. Convert `addAllToLibrary()` to use the suspend version at line 418
4. Verify all three call sites still compile and function correctly
:::

---

### 2. Replace deprecated `setWifiEnabled` in BaseResourceFragment

The `WifiManager.setWifiEnabled()` method has been deprecated since Android Q (API 29). Direct Wi-Fi toggling is no longer permitted by apps. The call at line 149 should be replaced with an intent to the Wi-Fi settings panel.

:codex-file-citation[codex-file-citation]{line_range_start=146 line_range_end=151 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L146-L151"}

:::task-stub{title="Replace deprecated setWifiEnabled in BaseResourceFragment"}
1. Remove the direct `wifi.setWifiEnabled(false)` call at line 149
2. Replace with `startActivity(Intent(Settings.Panel.ACTION_WIFI))` or `Settings.ACTION_WIFI_SETTINGS` intent
3. Handle the case where the panel action is not available on older devices
:::

---

### 3. Replace deprecated `userModel` property in BaseTeamFragment

The `profileDbHandler.userModel` property is deprecated at line 41. This is used during `onCreate` to set the `user` field for team-related operations.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L41-L41"}

:::task-stub{title="Replace deprecated userModel in BaseTeamFragment"}
1. Convert the `user` assignment at line 41 to use the `getUserModel()` suspend function
2. Wrap in `lifecycleScope.launch` within `onCreate` or move to a suspend-aware lifecycle callback
3. Ensure downstream usages of `user` handle the async initialization correctly
:::

---

### 4. Replace deprecated `getMyLibraryByUserId` in RealmMyLibrary

The static method `getMyLibraryByUserId(userId, libs, mRealm)` is deprecated in favor of `ResourcesRepository.getLibraryByUserId`. The internal call at line 191 uses this deprecated overload.

:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=192 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L188-L192"}

:::task-stub{title="Replace deprecated getMyLibraryByUserId in RealmMyLibrary"}
1. Identify all callers of the deprecated `getMyLibraryByUserId` companion function
2. Migrate callers to use `ResourcesRepository.getLibraryByUserId` instead
3. Remove or suppress the deprecated function once all callers are migrated
:::

---

### 5. Fix always-false conditions in ConfigurationsRepositoryImpl

The compiler detects that null checks at lines 299 and 333 are always false because `apiInterface.getConfiguration()` returns a non-null `Response` type. These unreachable branches add dead code.

:codex-file-citation[codex-file-citation]{line_range_start=295 line_range_end=301 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L295-L301"}
:codex-file-citation[codex-file-citation]{line_range_start=329 line_range_end=335 path=app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt#L329-L335"}

:::task-stub{title="Remove always-false null checks in ConfigurationsRepositoryImpl"}
1. Remove the `if (versionsResponse == null)` block at line 299 since the response is non-null
2. Remove the `if (configResponse == null)` block at line 333 since the response is non-null
3. Replace with proper `isSuccessful` checks or exception handling for failed requests
:::

---

### 6. Replace deprecated `getUserModel()` calls in UserRepositoryImpl

The `getUserModel()` function is deprecated in favor of `getUserModelSuspending()`. Two call sites at lines 54 and 386 still use the old synchronous version.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L52-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=384 line_range_end=387 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L384-L387"}

:::task-stub{title="Replace deprecated getUserModel in UserRepositoryImpl"}
1. Update `getCurrentUser()` at line 54 to delegate to `getUserModelSuspending()` or remove if unused
2. Update `getActiveUserId()` at line 386 to delegate to `getActiveUserIdSuspending()`
3. Migrate remaining callers of these deprecated functions to their suspend equivalents
4. If these deprecated functions must remain for backward compatibility, suppress their internal warnings
:::

---

### 7. Replace deprecated `getUserModel()` calls in UserSessionManager

The `UserSessionManager` exposes a deprecated `userModel` property (line 42) and a deprecated `getUserModelCopy()` function (line 46), both calling the deprecated `getUserModel()`.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L41-L46"}

:::task-stub{title="Replace deprecated getUserModel in UserSessionManager"}
1. Update the `userModel` property at line 42 to internally call the non-deprecated suspend version or add `@Suppress("DEPRECATION")`
2. Update `getUserModelCopy()` at line 46 similarly
3. Add a new suspend-based accessor method if one does not already exist
4. Migrate all external callers of `userModel` and `getUserModelCopy()` to the new suspend API
:::

---

### 8. Replace deprecated `connectionInfo` in SyncManager

The `WifiManager.connectionInfo` property is deprecated since Android S (API 31). Modern code should use `ConnectivityManager.getNetworkCapabilities()` with `NetworkCapabilities.TRANSPORT_WIFI`.

:codex-file-citation[codex-file-citation]{line_range_start=530 line_range_end=533 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L530-L533"}

:::task-stub{title="Replace deprecated connectionInfo in SyncManager"}
1. Replace `wifiManager.connectionInfo` at line 532 with `ConnectivityManager.getNetworkCapabilities()`
2. Check for `NetworkCapabilities.TRANSPORT_WIFI` instead of `SupplicantState.COMPLETED`
3. Add appropriate API level checks or use `NetworkCallback` for robust detection
:::

---

### 9. Replace deprecated `serializeNews` in UploadConfigs

The `RealmNews.serializeNews` function is deprecated in favor of `ChatRepository.serializeNews`. The `UploadConfigs.News` config at line 213 uses the deprecated serializer.

:codex-file-citation[codex-file-citation]{line_range_start=209 line_range_end=216 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L209-L216"}

:::task-stub{title="Replace deprecated serializeNews in UploadConfigs"}
1. Replace `RealmNews::serializeNews` at line 213 with a reference to `ChatRepository.serializeNews`
2. Ensure the `ChatRepository` instance is accessible within the `UploadConfigs` object
3. Update the serializer type if the method signature differs
:::

---

### 10. Replace deprecated `userModel` property in CourseDetailFragment

The `profileDbHandler.userModel` property is deprecated at line 44 in `CourseDetailFragment.onCreateView()`.

:codex-file-citation[codex-file-citation]{line_range_start=42 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailFragment.kt#L42-L45"}

:::task-stub{title="Replace deprecated userModel in CourseDetailFragment"}
1. Replace `profileDbHandler.userModel` at line 44 with the suspend `getUserModel()` function
2. Move initialization into a coroutine scope (e.g., `viewLifecycleOwner.lifecycleScope.launch`)
3. Ensure the `user` field is set before it is read by dependent methods
:::

---

### 11. Replace deprecated `userModel` in BellDashboardFragment

The `profileDbHandler.userModel` property is deprecated at line 64 in `onCreateView()`.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L59-L65"}

:::task-stub{title="Replace deprecated userModel in BellDashboardFragment"}
1. Replace `profileDbHandler.userModel` at line 64 with the suspend `getUserModel()` function
2. Wrap in `viewLifecycleOwner.lifecycleScope.launch` and ensure view state is ready
:::

---

### 12. Replace deprecated `userModel` and `getActionView` in DashboardActivity

The `userSessionManager.userModel` property is deprecated at lines 144 and 689. Additionally, `MenuItemCompat.getActionView()` at line 640 is deprecated in favor of `MenuItem.actionView`.

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=144 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L144-L144"}
:codex-file-citation[codex-file-citation]{line_range_start=640 line_range_end=640 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L640-L640"}
:codex-file-citation[codex-file-citation]{line_range_start=689 line_range_end=689 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L689-L689"}

:::task-stub{title="Replace deprecated userModel and getActionView in DashboardActivity"}
1. Replace `userSessionManager.userModel` at line 144 with the suspend `getUserModel()` function
2. Replace `MenuItemCompat.getActionView(menuItem)` at line 640 with `menuItem.actionView`
3. Replace `profileDbHandler.userModel` at line 689 with the suspend version
4. Wrap coroutine-dependent initialization in `lifecycleScope.launch`
:::

---

### 13. Replace deprecated WiFi and network APIs in DashboardElementActivity

Multiple deprecated Android networking APIs are used: `getNetworkInfo()`, `TYPE_WIFI`, `isConnected`, `isWifiEnabled`, `configuredNetworks`, `networkId`, and `enableNetwork()`. These should be replaced with modern `ConnectivityManager` and `NetworkCapabilities` APIs.

:codex-file-citation[codex-file-citation]{line_range_start=174 line_range_end=180 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L174-L180"}
:codex-file-citation[codex-file-citation]{line_range_start=186 line_range_end=209 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L186-L209"}

:::task-stub{title="Replace deprecated WiFi/network APIs in DashboardElementActivity"}
1. Replace `connManager.getNetworkInfo(TYPE_WIFI)` at line 176 with `getNetworkCapabilities()` check
2. Replace `mWifi?.isConnected` at line 179 with `NetworkCapabilities.hasTransport(TRANSPORT_WIFI)`
3. Replace `wifi.isWifiEnabled = false/true` at lines 180 and 188 with `Settings.Panel.ACTION_WIFI` intent
4. Replace `wifiManager.configuredNetworks` at line 206 with `NetworkSuggestion` API or remove
5. Replace `wifiManager.enableNetwork()` at line 209 with modern network suggestion/request API
6. Handle backward compatibility for API levels below 29
:::

---

### 14. Fix unchecked cast in NotificationsFragment

An unchecked cast of `Serializable?` to `Triple<String, String?, String?>` at line 123 generates a compiler warning. This should use safe casting with a type check.

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt#L122-L125"}

:::task-stub{title="Fix unchecked cast in NotificationsFragment"}
1. Replace `result as? Triple<String, String?, String?>` at line 123 with a safe pattern using `@Suppress("UNCHECKED_CAST")` or restructure to avoid the generic cast
2. Consider using a data class instead of `Triple` for type-safe serialization
:::

---

### 15. Fix parameter naming mismatch in CollectionsFragment

The overriding function parameter at line 131 uses a different name than the supertype `OnTagClickListener` parameter named `tag`. This can cause issues with named arguments.

:codex-file-citation[codex-file-citation]{line_range_start=131 line_range_end=137 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt#L131-L137"}

:::task-stub{title="Fix parameter naming mismatch in CollectionsFragment"}
1. Rename the parameter in `onCheckboxTagSelected` at line 131 from `tags` to `tag` to match the supertype
2. Update all references to the parameter within the function body
:::

---

### 16. Replace deprecated `userModel` in SurveyFragment and SurveysViewModel

The `profileDbHandler.userModel` and `userSessionManager.userModel` properties are deprecated in survey-related files at three locations.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L60-L60"}
:codex-file-citation[codex-file-citation]{line_range_start=77 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L77-L77"}
:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L208-L208"}

:::task-stub{title="Replace deprecated userModel in SurveyFragment and SurveysViewModel"}
1. Replace `profileDbHandler.userModel` at SurveyFragment line 60 with the suspend `getUserModel()`
2. Replace `userSessionManager.userModel` at SurveysViewModel line 77 with the suspend version
3. Replace `userSessionManager.userModel` at SurveysViewModel line 208 with the suspend version
4. Ensure ViewModel coroutine scopes are used properly for the async calls
:::

---

### 17. Replace deprecated `userModel` in LoginActivity

Two consecutive accesses to `profileDbHandler.userModel` at lines 619-620 use the deprecated property to obtain user image and name.

:codex-file-citation[codex-file-citation]{line_range_start=618 line_range_end=621 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L618-L621"}

:::task-stub{title="Replace deprecated userModel in LoginActivity"}
1. Replace both `profileDbHandler.userModel` accesses at lines 619-620 with a single suspend call
2. Cache the result in a local variable to avoid redundant calls
3. Wrap in `lifecycleScope.launch` if not already within a coroutine
:::

---

### 18. Fix unchecked cast in RealtimeSyncMixin

An unchecked cast of `ListAdapter<*, *>` to `ListAdapter<Any, *>` at line 85 generates a compiler warning due to type erasure.

:codex-file-citation[codex-file-citation]{line_range_start=82 line_range_end=86 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L82-L86"}

:::task-stub{title="Fix unchecked cast in RealtimeSyncMixin"}
1. Add `@Suppress("UNCHECKED_CAST")` to the cast at line 85
2. Alternatively, refactor to use a type-safe interface or callback pattern
:::

---

### 19. Replace deprecated `userModel` in AchievementFragment and EditAchievementFragment

Both achievement fragments use the deprecated `profileDbHandler.userModel` property in their `onCreateView()` methods.

:codex-file-citation[codex-file-citation]{line_range_start=79 line_range_end=81 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L79-L81"}
:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L63-L65"}

:::task-stub{title="Replace deprecated userModel in AchievementFragment and EditAchievementFragment"}
1. Replace `profileDbHandler.userModel` at AchievementFragment line 81 with the suspend version
2. Replace `profileDbHandler.userModel` at EditAchievementFragment line 65 with the suspend version
3. Use `viewLifecycleOwner.lifecycleScope.launch` for coroutine initialization
:::

---

### 20. Fix unchecked cast in VoicesFragment

An unchecked cast from `SpinnerAdapter!` to `ArrayAdapter<String>` at line 320 may fail at runtime if the adapter type differs.

:codex-file-citation[codex-file-citation]{line_range_start=318 line_range_end=321 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L318-L321"}

:::task-stub{title="Fix unchecked cast in VoicesFragment"}
1. Add a type check before the cast at line 320 or use `@Suppress("UNCHECKED_CAST")`
2. Consider storing the adapter reference as a typed field to avoid runtime casting
:::

---

### 21. Replace deprecated `Thread.id` in ANRWatchdog

The `Thread.id` property (Java `Thread.getId()`) is deprecated. The replacement is `Thread.threadId()` (API 35+) or use the thread name for logging purposes.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt#L54-L58"}

:::task-stub{title="Replace deprecated Thread.id in ANRWatchdog"}
1. Replace `mainThread.id` at line 57 with `mainThread.threadId()` guarded by an API level check
2. For API levels below 35, fall back to `@Suppress("DEPRECATION")` with the old `id` property
3. Alternatively, use the thread name instead of the numeric ID for the ANR message
:::

---

### 22. Replace deprecated `userModel` in DialogUtils

The `profileDbHandler.userModel` property is deprecated at line 57 in `DialogUtils`, used to get the username for the become-member intent.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L54-L58"}

:::task-stub{title="Replace deprecated userModel in DialogUtils"}
1. Replace `profileDbHandler.userModel?.name` at line 57 with the suspend `getUserModel()` version
2. Make the calling function suspend-aware or pass the username as a parameter
:::

---

### 23. Replace deprecated `statusBarColor` and `navigationBarColor` in EdgeToEdgeUtils

The `Window.statusBarColor` and `Window.navigationBarColor` properties are deprecated in API 35+. The modern approach uses `WindowInsetsController` or the edge-to-edge APIs.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtils.kt#L39-L41"}

:::task-stub{title="Replace deprecated statusBarColor/navigationBarColor in EdgeToEdgeUtils"}
1. Replace `statusBarColor = Color.TRANSPARENT` at line 40 with AndroidX `WindowCompat` edge-to-edge setup
2. Replace `navigationBarColor = Color.TRANSPARENT` at line 41 similarly
3. Use `WindowCompat.setDecorFitsSystemWindows(window, false)` and `WindowInsetsControllerCompat` for system bar appearance
:::

---

### 24. Replace deprecated `connectionInfo` in NetworkUtils

The `WifiManager.connectionInfo` property at line 157 is deprecated since API 31. Use `ConnectivityManager.getNetworkCapabilities()` instead.

:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=157 path=app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt#L155-L157"}

:::task-stub{title="Replace deprecated connectionInfo in NetworkUtils"}
1. Replace `wifiManager?.connectionInfo` at line 157 with `ConnectivityManager.getLinkProperties()` or `NetworkCapabilities`
2. Extract the needed Wi-Fi information (SSID, BSSID, etc.) from the modern API
3. Add API level guards for backward compatibility
:::

---

### 25. Replace deprecated EncryptedSharedPreferences and MasterKey in SecurePrefs

The `EncryptedSharedPreferences` and `MasterKey` classes from `androidx.security.crypto` are deprecated. The `getPrimitive()` method at line 37 is also deprecated in the Tink library.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L7-L8"}
:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=37 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L34-L37"}

:::task-stub{title="Replace deprecated crypto APIs in SecurePrefs"}
1. Evaluate if `EncryptedSharedPreferences` can be replaced with `EncryptedSharedPreferences.create()` from the updated library
2. Replace deprecated `MasterKey` usage with the latest AndroidX Security Crypto library API
3. Replace `keysetHandle.getPrimitive(Aead::class.java)` at line 37 with the non-deprecated Tink AEAD API
4. Verify data migration path for existing encrypted preferences
:::

---

### 26. Remove unnecessary safe call in SyncTimeLogger

The compiler flags an unnecessary safe call (`?.let`) at line 87 on a non-null receiver of type `String`. The safe call operator adds no value here.

:codex-file-citation[codex-file-citation]{line_range_start=83 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/42261e8f81d6dc3cbecd840b0139399210684edf/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L83-L88"}

:::task-stub{title="Remove unnecessary safe call in SyncTimeLogger"}
1. Replace `mapping.alternativeUrl?.let { alternativeUrl ->` at line 87 with direct access since it is non-null at this point
2. Use `val alternativeUrl = mapping.alternativeUrl` with a direct block instead of `?.let`
:::

---

## Testing

:::task-stub{title="Build verification after deprecation fixes"}
1. Run `./gradlew assembleDefaultDebug` to verify no new compilation errors
2. Run `./gradlew assembleLiteDebug` to verify lite flavor builds
3. Confirm the warning count has decreased in the build output
4. Verify no runtime regressions in Wi-Fi, sync, user session, and survey flows
:::
