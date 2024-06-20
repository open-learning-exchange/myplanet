package org.ole.planet.myplanet.utilities
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.*

object CameraUtils {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler
    private var backgroundThread: HandlerThread
    @JvmStatic
    fun CapturePhoto(context: Context, callback: ImageCaptureCallback) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        openCamera(context)
        imageReader = ImageReader.newInstance(
            IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1
        )
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            savePicture(bytes, callback)
            image.close()
        }, backgroundHandler)

        val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder?.addTarget(imageReader!!.surface)
        captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
            }
        }

        captureSession?.stopRepeating()
        captureSession?.abortCaptures()
        captureSession?.capture(captureBuilder!!.build(), captureCallback, null)
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
    @SuppressLint("MissingPermission")
    @JvmStatic
    private fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0] // Assuming we want to use the first (rear) camera
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val largest = Collections.max(
                listOf(*map!!.getOutputSizes(ImageFormat.JPEG)),
                CompareSizesByArea()
            )
            val reader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2)
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice?.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreview() {
        try {
            val texture = SurfaceTexture(0)
            texture.setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT)
            val surface = Surface(texture)
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            captureRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            captureSession!!.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    interface ImageCaptureCallback {
        fun onImageCapture(fileUri: String?)
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }
    init {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)
    }
    private const val IMAGE_WIDTH = 640
    private const val IMAGE_HEIGHT = 480
}