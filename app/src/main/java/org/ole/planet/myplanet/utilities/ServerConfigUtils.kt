package org.ole.planet.myplanet.utilities

import android.content.Context
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.ServerAddressesModel

object ServerConfigUtils {
    fun getServerAddresses(context: Context): List<ServerAddressesModel> {
        return listOf(
            ServerAddressesModel(context.getString(R.string.sync_planet_learning), BuildConfig.PLANET_LEARNING_URL),
            ServerAddressesModel(context.getString(R.string.sync_guatemala), BuildConfig.PLANET_GUATEMALA_URL),
            ServerAddressesModel(context.getString(R.string.sync_san_pablo), BuildConfig.PLANET_SANPABLO_URL),
            ServerAddressesModel(context.getString(R.string.sync_planet_earth), BuildConfig.PLANET_EARTH_URL),
            ServerAddressesModel(context.getString(R.string.sync_somalia), BuildConfig.PLANET_SOMALIA_URL),
            ServerAddressesModel(context.getString(R.string.sync_vi), BuildConfig.PLANET_VI_URL),
            ServerAddressesModel(context.getString(R.string.sync_xela), BuildConfig.PLANET_XELA_URL),
            ServerAddressesModel(context.getString(R.string.sync_uriur), BuildConfig.PLANET_URIUR_URL),
            ServerAddressesModel(context.getString(R.string.sync_ruiru), BuildConfig.PLANET_RUIRU_URL),
            ServerAddressesModel(context.getString(R.string.sync_embakasi), BuildConfig.PLANET_EMBAKASI_URL),
            ServerAddressesModel(context.getString(R.string.sync_cambridge), BuildConfig.PLANET_CAMBRIDGE_URL),
        )
    }

    fun getFilteredList(
        showAdditionalServers: Boolean,
        serverList: List<ServerAddressesModel>,
        pinnedUrl: String?
    ): List<ServerAddressesModel> {
        val pinnedServer = serverList.find { it.url == pinnedUrl }
        return if (showAdditionalServers) {
            serverList
        } else {
            val topThree = serverList.take(3).toMutableList()
            if (pinnedServer != null && !topThree.contains(pinnedServer)) {
                listOf(pinnedServer) + topThree
            } else {
                topThree
            }
        }
    }

    fun removeProtocol(url: String): String {
        return url.removePrefix("https://").removePrefix("http://")
    }

    fun getPinForUrl(url: String): String {
        val pinMap = mapOf(
            BuildConfig.PLANET_LEARNING_URL to BuildConfig.PLANET_LEARNING_PIN,
            BuildConfig.PLANET_GUATEMALA_URL to BuildConfig.PLANET_GUATEMALA_PIN,
            BuildConfig.PLANET_SANPABLO_URL to BuildConfig.PLANET_SANPABLO_PIN,
            BuildConfig.PLANET_EARTH_URL to BuildConfig.PLANET_EARTH_PIN,
            BuildConfig.PLANET_SOMALIA_URL to BuildConfig.PLANET_SOMALIA_PIN,
            BuildConfig.PLANET_VI_URL to BuildConfig.PLANET_VI_PIN,
            BuildConfig.PLANET_XELA_URL to BuildConfig.PLANET_XELA_PIN,
            BuildConfig.PLANET_URIUR_URL to BuildConfig.PLANET_URIUR_PIN,
            BuildConfig.PLANET_RUIRU_URL to BuildConfig.PLANET_RUIRU_PIN,
            BuildConfig.PLANET_EMBAKASI_URL to BuildConfig.PLANET_EMBAKASI_PIN,
            BuildConfig.PLANET_CAMBRIDGE_URL to BuildConfig.PLANET_CAMBRIDGE_PIN,
        )
        return pinMap[url] ?: ""
    }

    fun saveAlternativeUrl(
        url: String,
        password: String,
        settings: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
    ): String {
        val uri = android.net.Uri.parse(url)
        val (urlUser, urlPwd, couchdbURL) = if (url.contains("@")) {
            val userinfo = org.ole.planet.myplanet.ui.sync.ProcessUserDataActivity.getUserInfo(uri)
            Triple(userinfo[0], userinfo[1], url)
        } else {
            val user = "satellite"
            val dbUrl = "${uri.scheme}://$user:$password@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
            Triple(user, password, dbUrl)
        }

        editor.putString("serverPin", password)
        editor.putString("url_user", urlUser)
        editor.putString("url_pwd", urlPwd)
        editor.putString("url_Scheme", uri.scheme)
        editor.putString("url_Host", uri.host)
        editor.putString("alternativeUrl", url)
        editor.putString("processedAlternativeUrl", couchdbURL)
        editor.putBoolean("isAlternativeUrl", true)
        editor.apply()

        return couchdbURL
    }
}
