package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ServerAvailabilityRepositoryImpl @Inject constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface
) : ServerAvailabilityRepository {

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    override fun isPlanetAvailable(callback: ServerAvailabilityRepository.PlanetAvailableListener?) {
        val updateUrl = "${preferences.getString("serverURL", "")}"
        serverAvailabilityCache[updateUrl]?.let { (available, timestamp) ->
            if (System.currentTimeMillis() - timestamp < 30000) {
                if (available) {
                    callback?.isAvailable()
                } else {
                    callback?.notAvailable()
                }
                return
            }
        }

        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = isServerReachable(mapping.primaryUrl)
            val alternativeAvailable = mapping.alternativeUrl?.let { isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = preferences.edit()

                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, preferences)
                }
            }

            withContext(Dispatchers.Main) {
                retrofitInterface.isPlanetAvailable(UrlUtils.getUpdateUrl(preferences))?.enqueue(object : Callback<ResponseBody?> {
                    override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                        val isAvailable = callback != null && response.code() == 200
                        serverAvailabilityCache[updateUrl] = Pair(isAvailable, System.currentTimeMillis())
                        if (isAvailable) {
                            callback.isAvailable()
                        } else {
                            callback?.notAvailable()
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                        serverAvailabilityCache[updateUrl] = Pair(false, System.currentTimeMillis())
                        callback?.notAvailable()
                    }
                })
            }
        }
    }
}

