package org.ole.planet.myplanet.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.util.UUID

class AudioRecorderService {
    private var outputFile: String? = null
    private var myAudioRecorder: MediaRecorder? = null
    private var audioRecordListener: AudioRecordListener? = null
    private var caller: ActivityResultCaller? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    fun forceStop() {
        myAudioRecorder?.apply {
            stop()
            release()
        }
        myAudioRecorder = null
        audioRecordListener?.onError("Recording stopped")
    }

    fun setAudioRecordListener(audioRecordListener: AudioRecordListener): AudioRecorderService {
        this.audioRecordListener = audioRecordListener
        return this
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startRecording() {
        outputFile = createAudioFile()
        myAudioRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        myAudioRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)
            try {
                prepare()
                start()
                audioRecordListener?.onRecordStarted()
            } catch (e: Exception) {
                myAudioRecorder = null
                audioRecordListener?.onError(e.message)
            }
        }
    }

    private fun createAudioFile(): String? {
        var audioFileName: String
        var audioFile: File
        var attempt = 0
        do {
            audioFileName = "${UUID.randomUUID()}.aac"
            audioFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), audioFileName)
            attempt++
        } while (audioFile.exists() && attempt < 100)
        if (attempt >= 100) {
            return null
        }
        return audioFile.absolutePath
    }

    fun isRecording(): Boolean {
        return myAudioRecorder != null
    }

    fun stopRecording() {
        myAudioRecorder?.let { recorder ->
            try {
                if (isRecording()) {
                    recorder.stop()
                    recorder.release()
                }
            } catch (e: RuntimeException) {
                MainApplication.handleUncaughtException(e)
            } finally {
                myAudioRecorder = null
                audioRecordListener?.onRecordStopped(outputFile)
            }
        }
    }

    fun setCaller(caller: ActivityResultCaller, context: Context){
        this.caller = caller
        permissionLauncher =
            caller.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted){
                    toggleRecording()
                }
                else {
                    if (!shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.RECORD_AUDIO)) {
                        AlertDialog.Builder(context, R.style.AlertDialogTheme)
                            .setTitle(R.string.permission_required)
                            .setMessage(R.string.microphone_permission_required)
                            .setPositiveButton(R.string.settings) { dialog, _ ->
                                dialog.dismiss()
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri: Uri = Uri.fromParts("package", context.packageName, null)
                                intent.data = uri
                                context.startActivity(intent)
                            }
                            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                            .show()
                    } else {
                        Utilities.toast(context, "Microphone permission is required to record audio.")
                    }
                }
            }
    }

    fun onRecordClicked() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleRecording() {
        if (this.isRecording()) {
            this.stopRecording()
        } else {
            this.startRecording()
        }
    }

    interface AudioRecordListener {
        fun onRecordStarted()
        fun onRecordStopped(outputFile: String?)
        fun onError(error: String?)
    }
}