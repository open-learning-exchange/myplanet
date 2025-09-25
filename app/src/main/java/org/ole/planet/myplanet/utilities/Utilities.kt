package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.graphics.toColorInt
import fisk.chipcloud.ChipCloudConfig
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context

object Utilities {

    // only for DEBUG ... not #deadcode
    @JvmStatic
    fun log(message: String) {
        Log.d("OLE ", "log: $message")
    }

    fun isValidEmail(target: CharSequence): Boolean {
        return !target.isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    @JvmStatic
    fun toast(context: Context?, message: CharSequence?, duration: Int = Toast.LENGTH_LONG) {
        context ?: return
        MainApplication.applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, duration).show()
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
