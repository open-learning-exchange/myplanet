Let's see the current `savePicture`:
```kotlin
    @JvmStatic
    private fun savePicture(data: ByteArray, callback: ImageCaptureCallback) {
        // ... file operations
        try {
            FileOutputStream(mainPicture).use { fos ->
                fos.write(data)
            }
            callback.onImageCapture(mainPicture.absolutePath)
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }
```
If we make `savePicture` a `suspend` function, or just run it in a coroutine, we can do `withContext(Dispatchers.Main)` for `callback.onImageCapture(...)`.

Let's modify `CameraUtils` to be:
```kotlin
    private var cameraScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
```
Wait, if `CameraUtils` is an `object`, it is a singleton. When `release` is called, should we `cancel` the scope? If we cancel it, we cannot use it again unless we recreate it. The instructions do not say to cancel it. Or we can just leave it as an active scope. But wait, `release` did `stopBackgroundThread()`, which stopped the thread. So `cameraScope.cancel()` and recreating it in `capturePhoto()` or `openCamera()` would mimic the old behavior. Or we can just use `cameraScope.launch` and not cancel the scope, since dispatchers default is a thread pool that doesn't hold threads open indefinitely.

Let's look at `capturePhoto`:
```kotlin
    @JvmStatic
    fun capturePhoto(callback: ImageCaptureCallback) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        // startBackgroundThread()
        openCamera(context)
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)

            cameraScope.launch {
                savePicture(bytes, callback)
            }

            image.close()
        }, null) // backgroundHandler becomes null
```

Wait, `reader.acquireLatestImage()` reads the image. If it's run on the main thread, it might block slightly, but maybe that's okay, or we can launch the coroutine immediately?
```kotlin
        imageReader?.setOnImageAvailableListener({ reader ->
            cameraScope.launch {
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.capacity())
                buffer.get(bytes)
                savePicture(bytes, callback)
                image.close()
            }
        }, null)
```
If we do this, the `reader` might be closed or get new images while the coroutine is starting. But `acquireLatestImage()` is better called immediately.
```kotlin
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.capacity())
                buffer.get(bytes)
                image.close()
                cameraScope.launch {
                    savePicture(bytes, callback)
                }
            }
        }, null)
```
Is `backgroundHandler` used anywhere else?
1. `captureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)` -> change `backgroundHandler` to `null`.
2. `cameraDevice?.createCaptureSession(listOf(surface), object : ..., backgroundHandler)` -> change to `null`.
