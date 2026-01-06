package org.ole.planet.myplanet.base

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Utilities

abstract class BasePermissionActivity : AppCompatActivity() {
    fun checkPermission(strPermission: String?): Boolean {
        val result = strPermission?.let { ContextCompat.checkSelfPermission(this, it) }
        return result == PackageManager.PERMISSION_GRANTED
    }

    fun checkUsagesPermission() {
        if (!getUsagesPermission(this)) {
            Utilities.toast(this, getString(R.string.please_allow_usages_permission_to_myplanet_app))
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    fun getUsagesPermission(context: Context): Boolean {
        val appOps = context.getSystemService(APP_OPS_SERVICE) as AppOpsManager
        var mode = -1
        try {
            val method = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AppOpsManager::class.java.getMethod("unsafeCheckOpNoThrow", String::class.java, Int::class.javaPrimitiveType, String::class.java)
            } else {
                AppOpsManager::class.java.getMethod("checkOpNoThrow", String::class.java, Int::class.javaPrimitiveType, String::class.java)
            }
            mode = method.invoke(appOps, AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) as Int
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun requestAllPermissions() {
        val permissions = ArrayList<String>()
        if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (!checkPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionDeclaredInManifest(Manifest.permission.READ_MEDIA_IMAGES) &&
                !checkPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (isPermissionDeclaredInManifest(Manifest.permission.READ_MEDIA_VIDEO) &&
                !checkPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (isPermissionDeclaredInManifest(Manifest.permission.READ_MEDIA_AUDIO) &&
                !checkPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isPermissionDeclaredInManifest(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            if (isPermissionDeclaredInManifest(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                !checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (isPermissionDeclaredInManifest(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionDeclaredInManifest(Manifest.permission.POST_NOTIFICATIONS) &&
                !checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE_FILE)
        } else {
            onAllPermissionsGranted()
        }
    }

    private fun isPermissionDeclaredInManifest(permission: String): Boolean {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun requestMediaPermissions() {
        val permissions = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!checkPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (!checkPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (!checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE_MEDIA)
        } else {
            onMediaPermissionsGranted()
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionRationale()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE_NOTIFICATION)
                }
            }
        }
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS) &&
                    NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }

    fun getNotificationPermissionStatus(): NotificationPermissionStatus {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    NotificationPermissionStatus.GRANTED
                } else {
                    NotificationPermissionStatus.DISABLED_IN_SETTINGS
                }
            }
            checkPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    NotificationPermissionStatus.GRANTED
                } else {
                    NotificationPermissionStatus.DISABLED_IN_SETTINGS
                }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                NotificationPermissionStatus.SHOULD_SHOW_RATIONALE
            }
            else -> {
                NotificationPermissionStatus.DENIED
            }
        }
    }

    enum class NotificationPermissionStatus {
        GRANTED,
        DENIED,
        SHOULD_SHOW_RATIONALE,
        DISABLED_IN_SETTINGS
    }

    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Enable Notifications")
            .setMessage("Notifications help you stay updated with:\n\n" +
                    "• New surveys and assignments\n" +
                    "• Task deadlines and reminders\n" +
                    "• Team join requests\n" +
                    "• System updates and storage warnings\n\n" +
                    "You can always disable them later in Settings.")
            .setPositiveButton("Allow") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE_NOTIFICATION
                    )
                }
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onNotificationPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE_FILE -> {
                handleFilePermissionsResult(permissions, grantResults)
            }
            PERMISSION_REQUEST_CODE_NOTIFICATION -> {
                handleNotificationPermissionResult(permissions, grantResults)
            }
            PERMISSION_REQUEST_CODE_MEDIA -> {
                handleMediaPermissionsResult(permissions, grantResults)
            }
        }
    }

    private fun handleFilePermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        val deniedPermissions = mutableListOf<String>()
        val grantedPermissions = mutableListOf<String>()

        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i])
            } else {
                grantedPermissions.add(permissions[i])
            }
        }

        if (deniedPermissions.isNotEmpty()) {
            val mediaPermissions = listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val onlyMediaDenied = deniedPermissions.all { it in mediaPermissions }

            if (onlyMediaDenied) {
                showMediaPermissionsDeniedDialog(deniedPermissions)
            } else {
                onPermissionsDenied(deniedPermissions)
            }
        } else {
            onAllPermissionsGranted()
        }
    }

    private fun handleMediaPermissionsResult(permissions: Array<out String>, grantResults: IntArray) {
        val deniedPermissions = mutableListOf<String>()

        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i])
            }
        }

        if (deniedPermissions.isNotEmpty()) {
            onMediaPermissionsDenied(deniedPermissions)
        } else {
            onMediaPermissionsGranted()
        }
    }

    private fun handleNotificationPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        val notificationPermissionIndex = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)

        if (notificationPermissionIndex >= 0) {
            if (grantResults[notificationPermissionIndex] == PackageManager.PERMISSION_GRANTED) {
                onNotificationPermissionGranted()
            } else {
                onNotificationPermissionDenied()
            }
        }
    }

    private fun showMediaPermissionsDeniedDialog(deniedPermissions: List<String>) {
        val permissionNames = deniedPermissions.map { permission ->
            getPermissionDisplayName(permission)
        }.distinct().joinToString(", ")

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Media Access")
            .setMessage("$permissionNames access was denied. You can:\n\n" +
                    "• Continue using the app with limited functionality\n" +
                    "• Grant permissions later in Settings\n" +
                    "• Try again now\n\n" +
                    "Note: Some features may not work without these permissions.")
            .setPositiveButton("Try Again") { _, _ ->
                requestMediaPermissions()
            }
            .setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
                onMediaPermissionsDenied(deniedPermissions)
            }
            .setNeutralButton("Settings") { _, _ ->
                openAppSettings()
            }
            .show()
    }

    fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            e.printStackTrace()
        }
    }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            e.printStackTrace()
        }
    }

    fun showNotificationSettingsDialog() {
        val status = getNotificationPermissionStatus()
        val (title, message, positiveText) = when (status) {
            NotificationPermissionStatus.DISABLED_IN_SETTINGS -> Triple(
                "Notifications Disabled",
                "Notifications are disabled in your device settings. To receive important updates about surveys, tasks, and team activities, please enable notifications for this app.",
                "Open Settings"
            )
            NotificationPermissionStatus.DENIED -> Triple(
                "Enable Notifications",
                "Stay informed about new surveys, task assignments, team requests, and important updates. Notifications help you never miss important activities.",
                "Enable"
            )
            NotificationPermissionStatus.SHOULD_SHOW_RATIONALE -> Triple(
                "Why Enable Notifications?",
                "Notifications are essential for:\n\n" +
                        "📋 New surveys and forms\n" +
                        "✅ Task assignments and deadlines\n" +
                        "👥 Team join requests\n" +
                        "⚠️ Storage and system alerts\n" +
                        "📚 Course and library updates\n\n" +
                        "You can customize notification types in Settings later.",
                "Allow Notifications"
            )
            else -> return
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ ->
                when (status) {
                    NotificationPermissionStatus.DISABLED_IN_SETTINGS -> openNotificationSettings()
                    else -> requestNotificationPermission()
                }
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                onNotificationPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }

    fun ensureNotificationPermission(showRationale: Boolean = true) {
        val status = getNotificationPermissionStatus()

        when (status) {
            NotificationPermissionStatus.GRANTED -> {
                onNotificationPermissionGranted()
            }
            NotificationPermissionStatus.DENIED -> {
                if (showRationale) {
                    showNotificationSettingsDialog()
                } else {
                    requestNotificationPermission()
                }
            }
            NotificationPermissionStatus.SHOULD_SHOW_RATIONALE -> {
                showNotificationSettingsDialog()
            }
            NotificationPermissionStatus.DISABLED_IN_SETTINGS -> {
                if (showRationale) {
                    showNotificationSettingsDialog()
                } else {
                    openNotificationSettings()
                }
            }
        }
    }

    fun checkNotificationPermissionStatus() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_notification_check", 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastCheck > 24 * 60 * 60 * 1000) {
            if (!areNotificationsEnabled()) {
                onNotificationPermissionChanged(false)
            }
            prefs.edit { putLong("last_notification_check", currentTime) }
        }
    }

    open fun onAllPermissionsGranted() {}

    open fun onPermissionsDenied(deniedPermissions: List<String>) {}

    open fun onMediaPermissionsGranted() {}

    open fun onMediaPermissionsDenied(deniedPermissions: List<String>) {}

    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (permission) {
                        Manifest.permission.READ_MEDIA_IMAGES -> "Photos"
                        Manifest.permission.READ_MEDIA_VIDEO -> "Videos"
                        Manifest.permission.READ_MEDIA_AUDIO -> "Audio"
                        else -> "Required permission"
                    }
                } else {
                    when (permission) {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
                        Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                        else -> "Required permission"
                    }
                }
            }
        }
    }

    open fun onNotificationPermissionGranted() {
        Utilities.toast(this, "Notifications enabled! You'll receive important updates.")
    }

    open fun onNotificationPermissionDenied() {
        Utilities.toast(this, "You can enable notifications later in Settings to receive important updates.")
    }

    open fun onNotificationPermissionChanged(isEnabled: Boolean) {
        if (!isEnabled) {
            Utilities.toast(this, "Notifications are disabled. You can enable them in Settings to receive important updates.")
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE_FILE = 111
        const val PERMISSION_REQUEST_CODE_NOTIFICATION = 112
        const val PERMISSION_REQUEST_CODE_MEDIA = 113

        @JvmStatic
        fun hasInstallPermission(context: Context): Boolean {
            return !BuildConfig.LITE && context.packageManager.canRequestPackageInstalls()
        }
    }
}
