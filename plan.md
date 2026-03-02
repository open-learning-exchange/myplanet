1. **Create `SyncConfigurationCoordinator.kt`**:
   - Create class `SyncConfigurationCoordinator` in `org.ole.planet.myplanet.ui.sync`.
   - Take `ConfigurationsRepository`, `SharedPreferences`, `SharedPreferences.Editor` as constructor arguments.
   - Define a `Callback` interface:
     ```kotlin
     interface Callback {
         fun onCheckStarted()
         fun onCheckComplete()
         fun onCheckFailed(errorMessage: String)
         fun onVersionCheckRequired()
         fun onContinueSync(id: String, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
         fun onSaveConfigAndContinue(id: String, url: String, isAlternativeUrl: Boolean, defaultUrl: String)
         fun onClearDataDialogRequested(message: String, config: Boolean)
     }
     ```
   - Implement `checkMinApk(url, pin, callerActivity, serverConfigAction, currentDialogExists)` using suspend function/coroutines. Or, it could take an existing coroutine scope and `launch`. Let's just make `checkMinApk` a `suspend` function that returns a specific action state, or takes the `Callback` interface.

2. **Refactor `SyncActivity`**:
   - Keep `fun checkMinApk(url: String, pin: String, callerActivity: String)` signature in `SyncActivity` as required ("Keep existing callbacks/signatures so UI behavior does not change.").
   - Instantiate `SyncConfigurationCoordinator` in `SyncActivity`.
   - Update `SyncActivity.checkMinApk` to delegate to `SyncConfigurationCoordinator` within `lifecycleScope.launch`.
   - The coordinator's `handleConfigurationSuccess` logic needs access to `serverConfigAction`, `settings.getString`, `editor.putString`, `currentDialog` presence.

3. **Pre-commit Steps**:
   - `pre_commit_instructions` and verify tests.
