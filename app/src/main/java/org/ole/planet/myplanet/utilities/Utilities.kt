package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import fisk.chipcloud.ChipCloudConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.PREFS_NAME
import org.ole.planet.myplanet.datamanager.MyDownloadService
import org.ole.planet.myplanet.model.RealmMyLibrary
import java.lang.ref.WeakReference
import java.math.BigInteger

object Utilities {
    private var contextRef: WeakReference<Context>? = null

    fun setContext(ctx: Context) {
        contextRef = WeakReference(ctx.applicationContext)
    }

    val context: Context?
        get() = contextRef?.get()

    val SD_PATH: String by lazy {
        context?.getExternalFilesDir(null)?.let { "$it/ole/" } ?: ""
    }

    @JvmStatic
    fun log(message: String) {
        context?.let { Log.d("OLE ", "log: $message") } ?: Log.d("OLE ", "log: $message - Context is null")
    }

    fun getUrl(library: RealmMyLibrary?, settings: SharedPreferences?): String {
        return getUrl(library?.resourceId!!, library.resourceLocalAddress!!)
    }

    fun isValidEmail(target: CharSequence?): Boolean {
        return !TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target!!).matches()
    }

    fun getUrl(id: String, file: String): String {
        return "${getUrl()}/resources/$id/$file"
    }

    fun getUserImageUrl(userId: String, imageName: String): String {
        return "${getUrl()}/_users/$userId/$imageName"
    }

    fun openDownloadService(context: Context?, urls: ArrayList<String>) {
        val intent = Intent(context, MyDownloadService::class.java)
        intent.putStringArrayListExtra("urls", urls)
        context?.startService(intent)
    }

    @JvmStatic
    fun toast(context: Context?, s: String) {
        context ?: return
        Toast.makeText(context, s, Toast.LENGTH_LONG).show()
    }

    fun getCloudConfig(): ChipCloudConfig {
        return ChipCloudConfig()
            .useInsetPadding(true)
            .checkedChipColor(Color.parseColor("#e0e0e0"))
            .checkedTextColor(Color.parseColor("#000000"))
            .uncheckedChipColor(Color.parseColor("#e0e0e0"))
            .uncheckedTextColor(Color.parseColor("#000000"))
    }

    fun checkNA(s: String): String {
        return if (TextUtils.isEmpty(s)) "N/A" else s
    }

    fun getRelativeTime(timestamp: Long): String {
        val nowtime = System.currentTimeMillis()
        return if (timestamp < nowtime) {
            DateUtils.getRelativeTimeSpanString(timestamp, nowtime, 0).toString()
        } else "Just now"
    }

    fun getUserName(settings: SharedPreferences): String {
        return settings.getString("name", "") ?: ""
    }

    fun loadImage(userImage: String?, imageView: ImageView) {
        log("User image $userImage")
        if (!TextUtils.isEmpty(userImage)) {
            context?.let {
                Glide.with(it)
                    .load(userImage)
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(imageView)
            }
        } else {
            imageView.setImageResource(R.drawable.ole_logo)
        }
    }

    fun handleCheck(b: Boolean, i: Int, selectedItems: MutableList<Any>, list: List<*>) {
        if (b) {
            selectedItems.add(list[i]!!)
        } else if (selectedItems.contains(list[i])) {
            selectedItems.remove(list[i])
        }
    }

    val header: String
        get() {
            val settings = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            log("User " + settings?.getString("url_user", "") + " " + settings?.getString("url_pwd", ""))
            return "Basic " + Base64.encodeToString((settings?.getString("url_user", "") + ":" + settings?.getString("url_pwd", "")).toByteArray(), Base64.NO_WRAP)
        }

    fun getUrl(): String {
        val settings = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (settings != null) {
            if (settings.contains("couchdbURL")) {
                var url = settings.getString("couchdbURL", "")

                if (!url?.endsWith("/db")!!) {
                    url += "/db"
                }
                return url
            }
        }
        return ""
    }

    val hostUrl: String
    get() {
        val settings = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url: String?
        val scheme = settings?.getString("url_Scheme", "")
        val hostIp = settings?.getString("url_Host", "")
        if (settings?.contains("url_Host") == true) {
            url = if (settings.getString("url_Host", "")!!.endsWith(".org")) {
                "$scheme://$hostIp/ml/"
            } else {
                "$scheme://$hostIp:5000"
            }
            return url
        }
        return ""
    }
    
    fun getUpdateUrl(settings: SharedPreferences): String {
        var url = settings.getString("couchdbURL", "")
        if (url != null) {
            if (url.endsWith("/db")) {
                url = url.replace("/db", "")
            }
        }
        return "$url/versions"
    }

    fun getChecksumUrl(settings: SharedPreferences): String {
        var url = settings.getString("couchdbURL", "")
        if (url != null) {
            if (url.endsWith("/db")) {
                url = url.replace("/db", "")
            }
        }
        return "$url/fs/myPlanet.apk.sha256"
    }

    fun getHealthAccessUrl(settings: SharedPreferences): String {
        var url = settings.getString("couchdbURL", "")
        if (url != null) {
            if (url.endsWith("/db")) {
                url = url.replace("/db", "")
            }
        }
        return String.format("%s/healthaccess?p=%s", url, settings.getString("serverPin", "0000"))
    }

    fun getApkVersionUrl(settings: SharedPreferences): String {
        var url = settings.getString("couchdbURL", "")
        if (url != null) {
            if (url.endsWith("/db")) {
                url = url.replace("/db", "")
            }
        }
        return "$url/apkversion"
    }

    fun getApkUpdateUrl(path: String): String {
        val preferences = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var url = preferences?.getString("couchdbURL", "")
        if (url != null) {
            if (url.endsWith("/db")) {
                url = url.replace("/db", "")
            }
        }
        return "$url$path"
    }

    fun toHex(arg: String): String {
        return String.format("%x", BigInteger(1, arg.toByteArray()))
    }

    fun getMimeType(url: String?): String? {
        val extension = FileUtils.getFileExtension(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
