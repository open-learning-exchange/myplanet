1. **Create `SyncCoordinator` class**:
   - Add a new class `SyncCoordinator` in `org.ole.planet.myplanet.ui.sync`.
   - It will take `ConfigurationsRepository` and `SharedPreferences` in its constructor (or via injected factory).
   - Move `checkMinApk` and `handleConfigurationSuccess` logic from `SyncActivity` to this class.
   - It should have callbacks or coroutine returns so `SyncActivity` can react (e.g., `onConfigurationSuccess`, `onConfigurationFailure`, `onCheckApkStart`, `onCheckApkComplete`).
   - Define a `SyncCoordinatorCallback` interface for UI updates (like showing progress dialogs, `alertDialogOkay`, calling `continueSync`, `saveConfigAndContinue`, `clearDataDialog`, etc.).

2. **Refactor `SyncActivity`**:
   - Inject `SyncCoordinator` into `SyncActivity`.
   - Implement `SyncCoordinatorCallback` in `SyncActivity`.
   - Replace direct calls to `checkMinApk` with `syncCoordinator.checkMinApk(url, pin, callerActivity)`.
   - Remove `checkMinApk` and `handleConfigurationSuccess` methods from `SyncActivity`.

3. **Update dependencies**:
   - Update `DashboardElementActivity`, `LoginActivity`, and `ServerDialogExtensions.kt` to use the `SyncCoordinator` or keep calling it through `SyncActivity` if we make `SyncActivity` expose the coordinator or an alias method. (The task says "Keep existing callbacks/signatures so UI behavior does not change", so keeping `checkMinApk` on `SyncActivity` that just delegates to `SyncCoordinator` might be best).

Let's refine:
- The task says "Introduce a small coordinator class for `checkMinApk` + `handleConfigurationSuccess` flow. Keep existing callbacks/signatures so UI behavior does not change. Migrate only these methods in this PR".
- Let's create `SyncConfigurationCoordinator`.
