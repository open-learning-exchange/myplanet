package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DialogUtils.confirmDialog

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Activity.showPermissionDeniedFeedback(permission: String, @StringRes messageRes: Int) {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
        Utilities.toast(this, getString(messageRes))
    } else {
        confirmDialog(
            title = getString(R.string.permission_required),
            message = getString(messageRes),
            positiveText = getString(R.string.settings),
            onPositive = { IntentUtils.openAppSettings(this) },
            negativeText = getString(R.string.cancel)
        )
    }
}
