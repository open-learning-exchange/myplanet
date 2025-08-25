package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import fisk.chipcloud.ChipCloudConfig
import java.math.BigInteger
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

object Utilities {
    val SD_PATH: String by lazy {
        context.getExternalFilesDir(null)?.let { "$it/ole/" } ?: ""
    }

    @JvmStatic
    fun log(message: String) {
        Log.d("OLE ", "log: $message")
    }

    fun isValidEmail(target: CharSequence): Boolean {
        return !target.isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(target).matches()
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

    fun getUserName(settings: SharedPreferences): String {
        return settings.getString("name", "") ?: ""
    }

    fun loadImage(userImage: String?, imageView: ImageView) {
        if (!userImage.isNullOrEmpty()) {
            Glide.with(imageView.context)
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

    fun toHex(arg: String?): String {
        return arg?.toByteArray()?.let { String.format("%x", BigInteger(1, it)) } ?: ""
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
