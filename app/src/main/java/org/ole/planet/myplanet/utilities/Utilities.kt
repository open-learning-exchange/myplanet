package org.ole.planet.myplanet.utilities

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.Glide
import fisk.chipcloud.ChipCloudConfig
import java.lang.ref.WeakReference
import java.math.BigInteger
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DownloadWorker
import org.ole.planet.myplanet.datamanager.MyDownloadService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.UrlUtils

object Utilities {
    private var contextRef: WeakReference<Context>? = null

    fun setContext(ctx: Context) {
        contextRef = WeakReference(ctx.applicationContext)
    }

    val SD_PATH: String by lazy {
        context.getExternalFilesDir(null)?.let { "$it/ole/" } ?: ""
    }

    @JvmStatic
    fun log(message: String) {
        Log.d("OLE ", "log: $message")
    }

    fun getUrl(library: RealmMyLibrary?): String {
        return getUrl(library?.resourceId, library?.resourceLocalAddress)
    }

    fun isValidEmail(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    fun getUrl(id: String?, file: String?): String {
        return "${getUrl()}/resources/$id/$file"
    }

    fun getUserImageUrl(userId: String?, imageName: String): String {
        return "${getUrl()}/_users/$userId/$imageName"
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun openDownloadService(context: Context?, urls: ArrayList<String>, fromSync: Boolean) {
        context?.let { ctx ->
            val preferences = ctx.getSharedPreferences(MyDownloadService.PREFS_NAME, Context.MODE_PRIVATE)
            preferences.edit {
                putStringSet("url_list_key", urls.toSet())
            }
            startDownloadServiceSafely(ctx, "url_list_key", fromSync)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startDownloadServiceSafely(context: Context, urlsKey: String, fromSync: Boolean) {
        if (canStartForegroundService(context)) {
            try {
                MyDownloadService.startService(context, urlsKey, fromSync)
            } catch (e: Exception) {
                e.printStackTrace()
                handleForegroundServiceNotAllowed(context, urlsKey, fromSync)
            }
        } else {
            handleForegroundServiceNotAllowed(context, urlsKey, fromSync)
        }
    }

    private fun handleForegroundServiceNotAllowed(context: Context, urlsKey: String, fromSync: Boolean) {
        if (!fromSync) {
            toast(context, context.getString(R.string.download_in_background))
        }
        startDownloadWork(context, urlsKey, fromSync)
    }

    private fun startDownloadWork(context: Context, urlsKey: String, fromSync: Boolean) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                "urls_key" to urlsKey,
                "fromSync" to fromSync
            ))
            .addTag("download_work")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun canStartForegroundService(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> true
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                // Android 8-11: Check if app is in foreground or has special permissions
                isAppInForeground(context)
            }
            else -> {
                // Android 12+: More restrictions
                isAppInForeground(context) || hasSpecialForegroundPermissions(context)
            }
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && processInfo.processName == packageName
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasSpecialForegroundPermissions(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                alarmManager.canScheduleExactAlarms()
            }
            else -> false
        }
    }

    @JvmStatic
    fun toast(context: Context?, s: String?, duration: Int = Toast.LENGTH_LONG) {
        context ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, s, duration).show()
        }
    }

    fun getCloudConfig(): ChipCloudConfig {
        return ChipCloudConfig()
            .useInsetPadding(true)
            .checkedChipColor("#e0e0e0".toColorInt())
            .checkedTextColor("#000000".toColorInt())
            .uncheckedChipColor("#e0e0e0".toColorInt())
            .uncheckedTextColor("#000000".toColorInt())
    }

    fun checkNA(s: String?): String {
        return if (s.isNullOrEmpty()) "N/A" else s
    }

    fun getRelativeTime(timestamp: Long): String {
        val timeNow = System.currentTimeMillis()
        return if (timestamp < timeNow) {
            DateUtils.getRelativeTimeSpanString(timestamp, timeNow, 0).toString()
        } else "Just now"
    }

    fun getUserName(settings: SharedPreferences): String {
        return settings.getString("name", "") ?: ""
    }

    fun loadImage(userImage: String?, imageView: ImageView) {
        if (!TextUtils.isEmpty(userImage)) {
            Glide.with(context)
                .load(userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ole_logo)
        }
    }

    fun <T> handleCheck(b: Boolean, i: Int, selectedItems: MutableList<T?>, list: List<T?>) {
        if (b) {
            selectedItems.add(list[i])
        } else if (selectedItems.contains(list[i])) {
            selectedItems.remove(list[i])
        }
    }

    val header: String
        get() {
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return "Basic ${Base64.encodeToString(("${settings.getString("url_user", "")}:${ settings.getString("url_pwd", "") }").toByteArray(), Base64.NO_WRAP)}"
        }

    fun getUrl(): String {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return UrlUtils.dbUrl(settings)
    }

    val hostUrl: String
        get() {
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var scheme = settings.getString("url_Scheme", "")
            var hostIp = settings.getString("url_Host", "")
            val isAlternativeUrl = settings.getBoolean("isAlternativeUrl", false)
            val alternativeUrl = settings.getString("processedAlternativeUrl", "")

            if (isAlternativeUrl && !alternativeUrl.isNullOrEmpty()) {
                try {
                    val uri = alternativeUrl.toUri()
                    hostIp = uri.host ?: hostIp
                    scheme = uri.scheme ?: scheme
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return if (hostIp?.endsWith(".org") == true || hostIp?.endsWith(".gt") == true) {
                "$scheme://$hostIp/ml/"
            } else {
                "$scheme://$hostIp:5000/"
            }
        }

    fun getUpdateUrl(settings: SharedPreferences): String {
        val url = UrlUtils.baseUrl(settings)
        return "$url/versions"
    }

    fun getChecksumUrl(settings: SharedPreferences): String {
        val url = UrlUtils.baseUrl(settings)
        return "$url/fs/myPlanet.apk.sha256"
    }

    fun getHealthAccessUrl(settings: SharedPreferences): String {
        val url = UrlUtils.baseUrl(settings)
        return String.format("%s/healthaccess?p=%s", url, settings.getString("serverPin", "0000"))
    }

    fun getApkVersionUrl(settings: SharedPreferences): String {
        val url = UrlUtils.baseUrl(settings)
        return "$url/apkversion"
    }

    fun getApkUpdateUrl(path: String?): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = UrlUtils.baseUrl(preferences)
        return "$url$path"
    }

    fun toHex(arg: String?): String {
        return String.format("%x", BigInteger(1, arg?.toByteArray()))
    }

    fun getMimeType(url: String?): String? {
        val extension = FileUtils.getFileExtension(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    fun openPlayStore() {
        val appPackageName = context.packageName
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$appPackageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$appPackageName".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
