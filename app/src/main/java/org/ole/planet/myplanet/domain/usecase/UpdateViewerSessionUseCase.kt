package org.ole.planet.myplanet.domain.usecase

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

class UpdateViewerSessionUseCase @Inject constructor(
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) {

    sealed class ViewerSessionResult {
        object CheckingServer : ViewerSessionResult()
        object Connecting : ViewerSessionResult()
        data class Success(val responseHeader: Map<String, List<String>>) : ViewerSessionResult()
        data class Error(val message: String) : ViewerSessionResult()
    }

    operator fun invoke(): Flow<ViewerSessionResult> = flow {
        emit(ViewerSessionResult.CheckingServer)

        try {
            val serverUrl = sharedPrefManager.getServerUrl()
            val mapping = serverUrlMapper.processUrl(serverUrl)
            if (mapping.alternativeUrl != null) {
                serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                    serverUrlMapper.isUrlDirectlyReachable(url)
                }
            }

            emit(ViewerSessionResult.Connecting)

            while (true) {
                try {
                    val conn = getSessionUrl()?.openConnection() as HttpURLConnection? ?: throw Exception("Unable to get session URL")
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.doOutput = true
                    conn.doInput = true

                    val os = DataOutputStream(conn.outputStream)
                    os.writeBytes(getJsonObject().toString())

                    os.flush()
                    os.close()

                    val headers = conn.headerFields
                    conn.disconnect()

                    emit(ViewerSessionResult.Success(headers))
                } catch (e: Exception) {
                    emit(ViewerSessionResult.Error(e.message ?: "Unknown error"))
                }

                delay(15 * 60 * 1000L) // 15 minutes
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            emit(ViewerSessionResult.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(dispatcherProvider.io)

    private fun getJsonObject(): JSONObject? {
        return try {
            val jsonParam = JSONObject()
            jsonParam.put("name", sharedPrefManager.getUrlUser())
            jsonParam.put("password", sharedPrefManager.getUrlPwd())
            jsonParam
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getSessionUrl(): URL? {
        return try {
            val pref = UrlUtils.getUrl()
            val urlString = "$pref/_session"
            URL(urlString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
