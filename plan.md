# Plan to Replace HandlerThread with Coroutines in CameraUtils

1. **Remove `startBackgroundThread()` and `stopBackgroundThread()` functions:**
   - Delete both functions and any calls to them from `CameraUtils.kt`.
   - Remove `backgroundThread` and `backgroundHandler` variables.

2. **Refactor background camera operations to use a managed `CoroutineScope` with `Dispatchers.Default`:**
   - Add `private val cameraScope = CoroutineScope(Dispatchers.Default + SupervisorJob())` to `CameraUtils` object.
   - For callbacks requiring a handler (e.g., `setOnImageAvailableListener`, `setRepeatingRequest`, `createCaptureSession`), replace `backgroundHandler` with `null` so they run on the caller's looper (which is acceptable and standard when not using a dedicated HandlerThread).

3. **Migrate `backgroundHandler` posting to `withContext(Dispatchers.Main)` coroutine suspensions:**
   - In `capturePhoto`'s `setOnImageAvailableListener`, after extracting bytes from the `ImageReader`, launch a coroutine: `cameraScope.launch { savePicture(...) }`.
   - Update `savePicture` to be a `private suspend fun`. Inside it, wrap the `callback.onImageCapture(mainPicture.absolutePath)` execution with `withContext(Dispatchers.Main) { ... }` so that it returns to the main thread after the file saving operation completes.

4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
