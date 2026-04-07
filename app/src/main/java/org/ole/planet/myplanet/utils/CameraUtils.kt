package org.ole.planet.myplanet.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.context

object CameraUtils {
    private const val IMAGE_WIDTH = 640
    private const val IMAGE_HEIGHT = 480

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private val sessionExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }
    private var cameraJob = SupervisorJob()
    private var cameraScope: CoroutineScope? = null

    private fun startBackgroundThread(dispatcher: CoroutineDispatcher) {
        if (backgroundThread == null || backgroundThread?.isAlive == false) {
            backgroundThread = HandlerThread("CameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
        if (cameraJob.isCancelled) {
            cameraJob = SupervisorJob()
            cameraScope = CoroutineScope(dispatcher + cameraJob)
        } else if (cameraScope == null) {
            cameraScope = CoroutineScope(dispatcher + cameraJob)
        }
    }

    @JvmStatic
    fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun release() {
        closeCamera()
        stopBackgroundThread()
        cameraScope?.cancel()
    }

    @JvmStatic
    fun capturePhoto(dispatcher: CoroutineDispatcher, callback: ImageCaptureCallback) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        startBackgroundThread(dispatcher)
        openCamera(context)
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.capacity())
                buffer.get(bytes)
                image.close()
                cameraScope!!.launch {
                    savePicture(bytes, callback)
                }
            }
        }, backgroundHandler)

        try {
            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(imageReader!!.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {}

            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            captureSession?.capture(captureBuilder!!.build(), captureCallback, null)
        } catch (e: CameraAccessException) {
            when (e.reason) {
                CameraAccessException.CAMERA_DISCONNECTED -> {
                    reopenCamera(context)
                }
            }
        }
    }

    private fun reopenCamera(context: Context) {
        closeCamera()
        openCamera(context)
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private suspend fun savePicture(data: ByteArray, callback: ImageCaptureCallback) {
        val pictureFileDir = File("${FileUtils.getOlePath(context)}/userimages")
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
            pictureFileDir.mkdirs()
        }
        val photoFile = "${Date().time}.jpg"
        val filename = "${pictureFileDir.path}${File.separator}$photoFile"
        val mainPicture = File(filename)
        try {
            FileOutputStream(mainPicture).use { fos ->
                fos.write(data)
            }
            withContext(Dispatchers.Main) {
                callback.onImageCapture(mainPicture.absolutePath)
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    private fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera()
                    reopenCamera(context)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    closeCamera()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigurations = listOf(OutputConfiguration(surface))
                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }

                val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, sessionExecutor, stateCallback)

                cameraDevice?.createCaptureSession(sessionConfiguration)
            } else {
                @Suppress("DEPRECATION")
                cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {} },
                    backgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    interface ImageCaptureCallback {
        fun onImageCapture(fileUri: String?)
    }
}
