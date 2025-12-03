package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.graphics.toColorInt
import fisk.chipcloud.ChipCloudConfig
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication

object Utilities {
    fun isValidEmail(target: CharSequence): Boolean {
        return target.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    private fun getActivityFromContext(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    @JvmStatic
    fun toast(context: Context?, message: CharSequence?, duration: Int = Toast.LENGTH_LONG) {
        context ?: return
        MainApplication.applicationScope.launch(Dispatchers.Main) {
            val visualContext = getActivityFromContext(context) ?: context

            try {
                Toast.makeText(visualContext, message, duration).show()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
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

    fun toHex(arg: String?): String {
        return arg?.toByteArray()?.let { String.format("%x", BigInteger(1, it)) } ?: ""
    }

    fun getMimeType(url: String?): String? {
        val extension = FileUtils.getFileExtension(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
