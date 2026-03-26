package org.ole.planet.myplanet.utils

import android.util.Base64
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager

object UrlUtils {
    private fun spm(): SharedPrefManager =
        EntryPointAccessors.fromApplication(context, AutoSyncEntryPoint::class.java).sharedPrefManager()

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
                    e.printStackTrace()
                }
            }

            val finalUrl = if (hostIp?.endsWith(".org") == true || hostIp?.endsWith(".gt") == true) {
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

    fun getUrl(library: RealmMyLibrary?): String {
        return getUrl(library?.resourceId, library?.resourceLocalAddress)
    }

    fun getUrl(id: String?, file: String?): String {
        return "${getUrl()}/resources/$id/$file"
    }

    fun getUserImageUrl(userId: String?, imageName: String): String? {
        if (userId.isNullOrBlank() || imageName.isBlank()) {
            return null
        }
        val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
        val encodedImageName = java.net.URLEncoder.encode(imageName, "UTF-8").replace("+", "%20")
        return "${getUrl()}/_users/$encodedUserId/$encodedImageName"
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
