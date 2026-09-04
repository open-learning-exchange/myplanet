package org.ole.planet.myplanet.utils

import android.util.Log
import java.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import java.net.URLEncoder
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager

object UrlUtils {
    @Volatile
    private var spmInstance: SharedPrefManager? = null

    @Volatile
    private var cachedHeader: String? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var generation = 0

    fun init(sharedPrefManager: SharedPrefManager) {
        synchronized(this) {
            generation++
            spmInstance = sharedPrefManager
            cachedHeader = null
            cachedBaseUrl = null
        }
    }

    private fun spm(): SharedPrefManager {
        return spmInstance
            ?: error("UrlUtils.init(SharedPrefManager) must be called before using UrlUtils")
    }

    fun invalidateHeaderCache() {
        synchronized(this) {
            generation++
            cachedHeader = null
            cachedBaseUrl = null
        }
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(this) {
            generation++
            spmInstance = null
            cachedHeader = null
            cachedBaseUrl = null
        }
    }

    val header: String
        get() {
            cachedHeader?.let { return it }
            val currentGen: Int
            val user: String
            val pwd: String
            synchronized(this) {
                cachedHeader?.let { return it }
                currentGen = generation
                val spm = spm()
                user = spm.getUrlUser()
                pwd = spm.getUrlPwd()
            }
            val computed = basicAuthHeader(user, pwd)
            synchronized(this) {
                if (generation == currentGen) {
                    cachedHeader = computed
                }
            }
            return computed
        }

    fun basicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password".toByteArray()
        return "Basic ${Base64.getEncoder().encodeToString(credentials)}"
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
        cachedBaseUrl?.let { return it }
        val currentGen: Int
        var rawUrl: String
        synchronized(this) {
            cachedBaseUrl?.let { return it }
            currentGen = generation
            val isAlternativeUrl = spm.isAlternativeUrl()
            rawUrl = if (isAlternativeUrl) {
                spm.getProcessedAlternativeUrl()
            } else {
                spm.getCouchdbUrl()
            }
        }
        if (rawUrl.endsWith("/db")) {
            rawUrl = rawUrl.removeSuffix("/db")
        }
        synchronized(this) {
            if (generation == currentGen) {
                cachedBaseUrl = rawUrl
            }
        }
        return rawUrl
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
        return getUrl(id, file, getUrl())
    }

    fun getUrl(id: String?, file: String?, base: String): String {
        return "$base/resources/$id/$file"
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
        return "$url/healthaccess?p=${spm.getServerPin().ifEmpty { "0000" }}"
    }

    fun getApkVersionUrl(spm: SharedPrefManager): String {
        val url = baseUrl(spm)
        return "$url/apkversion"
    }

    fun getApkUpdateUrl(path: String?): String {
        val url = baseUrl(spm())
        return "$url$path"
    }

    fun getUserInfo(userInfo: String?): Pair<String, String> {
        val info = userInfo?.split(":")?.dropLastWhile { it.isEmpty() }
        return if (info != null && info.size > 1) {
            Pair(info[0], info[1])
        } else {
            Pair("", "")
        }
    }
}
