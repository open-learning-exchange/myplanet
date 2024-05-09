package org.ole.planet.myplanet.utilities

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import java.io.File
import java.io.FileOutputStream
import java.util.Date

object CameraUtils {
    @JvmStatic
    fun CapturePhoto(callback: ImageCaptureCallback) {
        val cameraInfo = CameraInfo()
        val frontCamera = 1
        val camera: Camera = try {
            Camera.getCameraInfo(frontCamera, cameraInfo)
            Camera.open(frontCamera)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            return
        }
        try {
            camera.setPreviewTexture(SurfaceTexture(0))
            camera.startPreview()
            camera.takePicture(null, null) { data, camera ->
                savePicture(data, callback)
                camera.release()
            }
        } catch (e: Exception) {
            camera.release()
        }
    }

    @JvmStatic
    private fun savePicture(data: ByteArray, callback: ImageCaptureCallback) {
        val pictureFileDir = File(Utilities.SD_PATH + "/userimages")
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
            pictureFileDir.mkdirs()
        }
        val photoFile = Date().time.toString() + ".jpg"
        val filename = pictureFileDir.path + File.separator + photoFile
        val mainPicture = File(filename)
        try {
            val fos = FileOutputStream(mainPicture)
            fos.write(data)
            fos.close()
            callback.onImageCapture(mainPicture.absolutePath)
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    interface ImageCaptureCallback {
        fun onImageCapture(fileUri: String?)
    }
}