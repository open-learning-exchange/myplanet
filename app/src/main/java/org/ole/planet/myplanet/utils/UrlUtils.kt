package org.ole.planet.myplanet.utils

import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import java.net.URLEncoder
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager

object UrlUtils {
    @Volatile
    private var spmInstance: SharedPrefManager? = null

    fun init(sharedPrefManager: SharedPrefManager) {
        spmInstance = sharedPrefManager
    }

    private fun spm(): SharedPrefManager {
        return spmInstance
            ?: error("UrlUtils.init(SharedPrefManager) must be called before using UrlUtils")
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        spmInstance = null
    }

    val header: String
        get() {
            val spm = spm()
            val credentials = "${spm.getUrlUser()}:${spm.getUrlPwd()}".toByteArray()
            return "Basic ${Base64.encodeToString(credentials, Base64.NO_WRAP)}"
        }

    val hostUrl: String
        get() {
            val spm = spm()
            var scheme = spm.getUrlScheme()
            var hostIp = spm.getUrlHost()
            val isAlternativeUrl = spm.isAlternativeUrl()
            val alternativeUrl = spm.getProcessedAlternativeUrl()


            if (isAlternativeUrl && !alternativeUrl.isNullOrEmpty()) {
                try {
                    val uri = alternativeUrl.toUri()
                    hostIp = uri.host ?: hostIp
                    scheme = uri.scheme ?: scheme
                } catch (e: Exception) {
                    Log.w("UrlUtils", "Failed to parse alternative URL '$alternativeUrl', falling back to host", e)
                }
            }

            val finalUrl = if (hostIp.endsWith(".org") || hostIp.endsWith(".gt")) {
                "$scheme://$hostIp/ml/"
            } else {
                "$scheme://$hostIp:5000/"
            }
            return finalUrl
        }
    fun baseUrl(spm: SharedPrefManager): String {
        val isAlternativeUrl = spm.isAlternativeUrl()
        var url = if (isAlternativeUrl) {
            spm.getProcessedAlternativeUrl()
        } else {
            spm.getCouchdbUrl()
        }
        if (url.endsWith("/db")) {
            url = url.removeSuffix("/db")
        }
        return url
    }

    fun dbUrl(spm: SharedPrefManager): String {
        val base = baseUrl(spm)
        return if (base.endsWith("/db")) base else "$base/db"
    }

    fun dbUrl(url: String): String {
        var base = url
        if (base.endsWith("/")) {
            base = base.dropLast(1)
        }
        return if (base.endsWith("/db")) base else "$base/db"
    }

    fun getUrl(library: MyLibrary?): String {
        return getUrl(library?.resourceId, library?.resourceLocalAddress)
    }

    fun getUrl(id: String?, file: String?): String {
        return "${getUrl()}/resources/$id/$file"
    }

    fun getUserImageUrl(userId: String?, imageName: String): String? {
        if (userId.isNullOrBlank() || imageName.isBlank()) {
            return null
        }
        val encodedUserId = URLEncoder.encode(userId, "UTF-8")
        val encodedImageName = URLEncoder.encode(imageName, "UTF-8").replace("+", "%20")
        return "${getUrl()}/_users/$encodedUserId/$encodedImageName"
    }

    fun getCourseImageUrl(courseId: String?, imageName: String?): String? {
        if (courseId.isNullOrBlank() || imageName.isNullOrBlank()) {
            return null
        }
        val encodedCourseId = URLEncoder.encode(courseId, "UTF-8")
        val encodedImageName = URLEncoder.encode(imageName, "UTF-8").replace("+", "%20")
        return "${getUrl()}/courses/$encodedCourseId/$encodedImageName"
    }

    fun getUrl(): String {
        return dbUrl(spm())
    }

    fun getUpdateUrl(spm: SharedPrefManager): String {
        val url = baseUrl(spm)
        return "$url/versions"
    }

    fun getChecksumUrl(spm: SharedPrefManager): String {
        val url = baseUrl(spm)
        return "$url/fs/myPlanet.apk.sha256"
    }

    fun getHealthAccessUrl(spm: SharedPrefManager): String {
        val url = baseUrl(spm)
        return String.format("%s/healthaccess?p=%s", url, spm.getServerPin().ifEmpty { "0000" })
    }

    fun getApkVersionUrl(spm: SharedPrefManager): String {
        val url = baseUrl(spm)
        return "$url/apkversion"
    }

    fun getApkUpdateUrl(path: String?): String {
        val url = baseUrl(spm())
        return "$url$path"
    }
}
