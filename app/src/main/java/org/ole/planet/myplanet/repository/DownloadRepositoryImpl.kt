package org.ole.planet.myplanet.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import java.io.IOException
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) : DownloadRepository {

    override suspend fun downloadFileResponse(url: String, authHeader: String): DownloadResult = withContext(dispatcherProvider.io) {
        try {
            val response = apiInterface.downloadFile(authHeader, url)
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody == null) {
                    return@withContext DownloadResult.Error("Empty response body")
                } else {
                    return@withContext DownloadResult.Success(responseBody)
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Unauthorized access"
                    403 -> "Forbidden - Access denied"
                    404 -> "File not found"
                    408 -> "Request timeout"
                    500 -> "Server error"
                    502 -> "Bad gateway"
                    503 -> "Service unavailable"
                    504 -> "Gateway timeout"
                    else -> "Connection failed (${response.code()})"
                }

                if (response.code() == 404) {
                    try {
                        val responseString = response.toString()
                        val regex = Regex("url=([^}]*)")
                        val matchResult = regex.find(responseString)
                        val extractedUrl = matchResult?.groupValues?.get(1)
                        createLog("File Not Found", "$extractedUrl")
                    } catch (e: Exception) {
                        createLog("File Not Found", url)
                    }
                }

                return@withContext DownloadResult.Error(errorMessage, response.code())
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext DownloadResult.Error("Server not reachable. Check internet connection.")
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext DownloadResult.Error("Connection timeout. Please try again.")
        } catch (e: java.net.ConnectException) {
            return@withContext DownloadResult.Error("Unable to connect to server")
        } catch (e: IOException) {
            return@withContext DownloadResult.Error("Network error: ${e.localizedMessage ?: "Unknown IO error"}")
        } catch (e: Exception) {
            return@withContext DownloadResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
