package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.Utilities

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Activity.showPermissionDeniedFeedback(permission: String, @StringRes messageRes: Int) {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
        Utilities.toast(this, getString(messageRes))
    } else {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.permission_required)
            .setMessage(messageRes)
            .setPositiveButton(R.string.settings) { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

fun Activity.openAppSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
        Log.e("PermissionExtensions", "ActivityNotFoundException for app settings", e)
    }
}
