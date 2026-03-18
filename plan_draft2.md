Wait, `ImageReader.setOnImageAvailableListener` and `CameraCaptureSession.setRepeatingRequest` take a `Handler` in older APIs.
In API 28+, `ImageReader` has `setOnImageAvailableListener` with `Executor`, but this app might support lower API levels (minSdkVersion is likely 21 or 24).
Wait, the prompt says:
"Replace HandlerThread with Coroutines in CameraUtils"
"1. Remove `startBackgroundThread()` and `stopBackgroundThread()` functions."
"2. Refactor background camera operations to use a managed `CoroutineScope` with `Dispatchers.Default`."
"3. Migrate `backgroundHandler` posting to `withContext(Dispatchers.Main)` coroutine suspensions."
