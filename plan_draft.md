1. Modify `app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt`:
   - Remove `android.os.Handler` and `android.os.HandlerThread`.
   - Add imports for `kotlinx.coroutines.*`.
   - Remove `backgroundHandler`, `backgroundThread` variables.
   - Add a `cameraScope` using `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
   - Remove `startBackgroundThread()` and `stopBackgroundThread()`.
   - Update `release()` to cancel the `cameraScope`? Wait, `object CameraUtils` might be used again. We shouldn't cancel the scope if we want to reuse it, or we should recreate it if needed. Actually, `cameraScope` can be `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. We could just launch coroutines. Wait, `ImageReader.setOnImageAvailableListener` and `CameraCaptureSession.setRepeatingRequest` take a `Handler`. If we remove `Handler`, how do we pass a coroutine to them?
