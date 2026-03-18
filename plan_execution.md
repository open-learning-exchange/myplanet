1. **Remove `startBackgroundThread()` and `stopBackgroundThread()` functions:**
   - Execute `sed` or `git apply` to remove the functions `startBackgroundThread` and `stopBackgroundThread`.
   - Remove references to `startBackgroundThread()` in `capturePhoto`.
   - Remove references to `stopBackgroundThread()` in `release`.
   - Remove `backgroundThread` and `backgroundHandler` variable declarations.

2. **Refactor background camera operations to use a managed `CoroutineScope` with `Dispatchers.Default`:**
   - Modify `CameraUtils.kt` using `replace_with_git_merge_diff` to add `private val cameraScope = CoroutineScope(Dispatchers.Default + SupervisorJob())`. Add `import kotlinx.coroutines.*`.
   - Replace `backgroundHandler` references in `setOnImageAvailableListener`, `setRepeatingRequest`, and `createCaptureSession` with `null`.

3. **Migrate `backgroundHandler` posting to `withContext(Dispatchers.Main)` coroutine suspensions:**
   - Modify `capturePhoto` using `replace_with_git_merge_diff` to wrap `savePicture` and related bytes operations in `cameraScope.launch`.
   - Modify `savePicture` to make it a `suspend` function and wrap `callback.onImageCapture` in `withContext(Dispatchers.Main)`.

4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
