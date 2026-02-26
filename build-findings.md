### Remove deprecated `android.newDsl=false` gradle property

The build warns that `android.newDsl=false` is deprecated with a current default of `true` and will be removed in AGP 10.0. Since the project already uses AGP 9.0.0, this override is unnecessary and should be deleted.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L28-L28"}

:::task-stub{title="Remove deprecated android.newDsl=false property"}
1. Delete line 28 (`android.newDsl=false`) from `gradle.properties`
:::

### Remove deprecated `android.enableJetifier=true` gradle property

The build warns that `android.enableJetifier=true` is deprecated with a current default of `false` and will be removed in AGP 10.0. Jetifier rewrites legacy support-library references to AndroidX, but all dependencies already use AndroidX directly.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L25"}

:::task-stub{title="Remove deprecated android.enableJetifier=true property"}
1. Delete line 25 (`android.enableJetifier=true`) from `gradle.properties`
2. Run `./gradlew assembleDefaultDebug assembleLiteDebug` to verify no support-library transitive dependency breaks
:::

### Suppress deprecation warnings in UserRepositoryImpl deprecated wrappers

`getCurrentUser()` and `getActiveUserId()` are themselves deprecated and intentionally delegate to the deprecated `getUserModel()`. The compiler warns about calling a deprecated method, but the call is deliberate since these wrappers exist solely for backward compatibility.

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L56-L59"}

:codex-file-citation[codex-file-citation]{line_range_start=397 line_range_end=400 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L397-L400"}

:::task-stub{title="Suppress deprecation in UserRepositoryImpl deprecated wrappers"}
1. Add `@Suppress("DEPRECATION")` above `getCurrentUser()` (before the existing `@Deprecated` annotation on line 56)
2. Add `@Suppress("DEPRECATION")` above `getActiveUserId()` (before the existing `@Deprecated` annotation on line 397)
:::

### Suppress deprecation warnings in UserSessionManager deprecated wrappers

The `userModel` property and `getUserModelCopy()` function are deprecated wrappers that deliberately call the deprecated `userRepository.getUserModel()`. The suspend-based `getUserModel()` replacement already exists on line 49.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L41-L47"}

:::task-stub{title="Suppress deprecation in UserSessionManager deprecated wrappers"}
1. Add `@Suppress("DEPRECATION")` above the `@Deprecated` annotation on the `userModel` property (line 41)
2. Add `@Suppress("DEPRECATION")` above the `@Deprecated` annotation on `getUserModelCopy()` (line 44)
:::

### Suppress deprecation on `wifiStatusSwitch()` in DashboardElementActivity

The `WifiManager.isWifiEnabled` setter is deprecated in Android Q, but this code is guarded by a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` early return on line 182. The deprecated path only runs on pre-Q devices where these APIs are the correct approach.

:codex-file-citation[codex-file-citation]{line_range_start=173 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L173-L211"}

:::task-stub{title="Suppress deprecation on wifiStatusSwitch()"}
1. Add `@Suppress("DEPRECATION")` annotation to `wifiStatusSwitch()` on line 174, alongside the existing `@SuppressLint("RestrictedApi")`
:::

### Suppress deprecation on `connectToWifi()` in DashboardElementActivity

The `configuredNetworks`, `networkId`, and `enableNetwork()` APIs are deprecated in Android Q. The method already has a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` guard that returns immediately on Q+ devices, so the deprecated code only runs where it is valid.

:codex-file-citation[codex-file-citation]{line_range_start=213 line_range_end=229 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L213-L229"}

:::task-stub{title="Suppress deprecation on connectToWifi()"}
1. Add `@Suppress("DEPRECATION")` annotation to `connectToWifi()` on line 213
:::

### Add file-level deprecation suppression in SecurePrefs

The build warns about importing the deprecated `EncryptedSharedPreferences` and `MasterKey` classes on lines 7–8. The file already has method-level `@Suppress("DEPRECATION")` on `getAead()` (line 31) and `getLegacyEncryptedPrefs()` (line 47), but the import statements themselves still trigger warnings. A file-level suppression covers imports and allows removing the redundant method-level annotations.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L7-L8"}

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L31-L31"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L47-L47"}

:::task-stub{title="Add file-level deprecation suppression in SecurePrefs"}
1. Add `@file:Suppress("DEPRECATION")` as the very first line of `SecurePrefs.kt` (before the `package` declaration)
2. Remove the now-redundant `@Suppress("DEPRECATION")` on `getAead()` (line 31)
3. Remove the now-redundant `@Suppress("DEPRECATION")` on `getLegacyEncryptedPrefs()` (line 47)
:::

### Suppress unchecked cast warning in RealtimeSyncMixin

The `ListAdapter<*, *>` is cast to `ListAdapter<Any, *>` on line 52. The cast is safe in practice because `submitList` and `currentList` only need the list identity, but Kotlin's type system cannot verify generic type arguments at runtime.

:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L51-L55"}

:::task-stub{title="Suppress unchecked cast in RealtimeSyncMixin"}
1. Add `@Suppress("UNCHECKED_CAST")` inside the `is ListAdapter<*, *>` branch, directly above the cast on line 52
:::

### Remove unnecessary safe call in SyncTimeLogger

On line 90, `mapping.alternativeUrl?.let { ... }` uses a safe call, but the condition on line 89 already guarantees `alternativeAvailable` is `true`, which means `alternativeUrl` was non-null (from the `?.let` on line 87). The safe call is redundant and triggers a compiler warning.

:codex-file-citation[codex-file-citation]{line_range_start=86 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L86-L91"}

:::task-stub{title="Remove unnecessary safe call in SyncTimeLogger"}
1. Change `mapping.alternativeUrl?.let { alternativeUrl ->` to `mapping.alternativeUrl!!.let { alternativeUrl ->` on line 90, or extract `val alternativeUrl = mapping.alternativeUrl!!` and remove the `let` block
:::

### Fix incorrect string resource concatenation in connectToWifi Toast

While analyzing the deprecated WiFi API warnings, line 222 reveals a bug: `R.string.you_are_now_connected + netId` performs integer addition on the resource ID and network ID instead of resolving the string resource. This results in displaying a wrong or missing string to the user.

:codex-file-citation[codex-file-citation]{line_range_start=222 line_range_end=222 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L222-L222"}

:::task-stub{title="Fix string resource concatenation in connectToWifi Toast"}
1. Change `R.string.you_are_now_connected + netId` to `getString(R.string.you_are_now_connected) + netId` on line 222
:::
