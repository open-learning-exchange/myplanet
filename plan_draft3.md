Wait, in `capturePhoto`, there is this:
```kotlin
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            savePicture(bytes, callback)
            image.close()
        }, backgroundHandler)
```
If we remove `backgroundHandler` and pass `null` or a main handler, the listener runs on that thread.
Then inside the listener, we do:
```kotlin
            cameraScope.launch(Dispatchers.Default) {
                // read bytes, save picture
                withContext(Dispatchers.Main) {
                    // post to callback?
                }
            }
```
Wait, the callback `onImageCapture(fileUri: String?)` should be called on the main thread?
If we look at the instructions again: "Migrate backgroundHandler posting to withContext(Dispatchers.Main) coroutine suspensions."
This implies we wrap the background operations in a coroutine, and then switch to Main to call the callback.
