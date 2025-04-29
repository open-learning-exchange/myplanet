package org.ole.planet.myplanet.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Utilities

class AudioRecorderPermission(
    private val caller: ActivityResultCaller,
    private val context: Context,
    private val recorder: AudioRecorderService?
){
    private val permissionLauncher =
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

    fun onRecordClicked() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    private fun toggleRecording() {
        if (recorder?.isRecording()!!) {
            recorder.stopRecording()
        } else {
            recorder.startRecording()
        }
    }
}