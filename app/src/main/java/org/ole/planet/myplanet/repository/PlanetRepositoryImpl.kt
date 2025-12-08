package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.UrlUtils
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class PlanetRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val preferences: SharedPreferences,
    private val context: Context,
) : PlanetRepository {
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    override suspend fun isPlanetAvailable(): Boolean = withContext(Dispatchers.IO) {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (System.currentTimeMillis() - timestamp < 30000) {
                return@withContext available
            }
        }

        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        val primaryReachable = MainApplication.isServerReachable(mapping.primaryUrl)
        val alternativeReachable = mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

        if (!primaryReachable && alternativeReachable) {
            mapping.alternativeUrl?.let { alternativeUrl ->
                val uri = updateUrl.toUri()
                val editor = preferences.edit()

                serverUrlMapper.updateUrlPreferences(
                    editor,
                    uri,
                    alternativeUrl,
                    mapping.primaryUrl,
                    preferences
                )
            }
        }
        try {
            val response = apiInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences)).execute()
            val isAvailable = response.code() == 200
            serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
            isAvailable
        } catch (e: Exception) {
            serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
            false
        }
    }
}
