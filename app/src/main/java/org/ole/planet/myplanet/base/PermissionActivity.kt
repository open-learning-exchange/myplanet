package org.ole.planet.myplanet.base

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Utilities

abstract class PermissionActivity : AppCompatActivity() {
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestAllPermissions() {
        val permissions = ArrayList<String>()
        if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (!checkPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!checkPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!checkPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (!checkPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (!checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE_FILE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE_FILE) {
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE_FILE = 111
        @JvmStatic
        fun hasInstallPermission(context: Context): Boolean {
            return context.packageManager.canRequestPackageInstalls()
        }
    }
}