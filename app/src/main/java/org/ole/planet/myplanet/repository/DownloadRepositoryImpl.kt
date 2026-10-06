package org.ole.planet.myplanet.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.api.PlanetApi
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

class DownloadRepositoryImpl @Inject constructor(
    private val planetApi: PlanetApi,
    private val dispatcherProvider: DispatcherProvider,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val timeProvider: TimeProvider
) : DownloadRepository {

    override suspend fun downloadFileResponse(url: String, authHeader: String, resumeOffset: Long, ifRange: String?): DownloadResult = withContext(dispatcherProvider.io) {
        try {
            val rangeHeader = if (resumeOffset > 0) "bytes=$resumeOffset-" else null
            val ifRangeHeader = if (resumeOffset > 0) ifRange else null
            val response = planetApi.downloadFile(authHeader, url, rangeHeader, ifRangeHeader)
            if (response.isSuccessful) {
                val responseBody = response.body
                if (responseBody == null) {
                    return@withContext DownloadResult.Error("Empty response body")
                } else {
                    val validator = response.header("ETag") ?: response.header("Last-Modified")
                    return@withContext DownloadResult.Success(responseBody, response.code, validator)
                }
            } else {
                val errorMessage = when (response.code) {
                    401 -> "Unauthorized access"
                    403 -> "Forbidden - Access denied"
                    404 -> "File not found"
                    408 -> "Request timeout"
                    416 -> "Requested range not satisfiable"
                    500 -> "Server error"
                    502 -> "Bad gateway"
                    503 -> "Service unavailable"
                    504 -> "Gateway timeout"
                    else -> "Connection failed (${response.code})"
                }

                if (response.code == 404) diagnosticsRepository.saveLogToRoom("File Not Found", diagnosticUrl(response.requestUrl ?: url), "${timeProvider.now()}")

                return@withContext DownloadResult.Error(errorMessage, response.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            return@withContext DownloadResult.Error("Server not reachable. Check internet connection.")
        } catch (e: SocketTimeoutException) {
            return@withContext DownloadResult.Error("Connection timeout. Please try again.")
        } catch (e: ConnectException) {
            return@withContext DownloadResult.Error("Unable to connect to server")
        } catch (e: IOException) {
            return@withContext DownloadResult.Error("Network error: ${e.localizedMessage ?: "Unknown IO error"}")
        } catch (e: Exception) {
            return@withContext DownloadResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}

/**
 * [url] is a canonical URL as the HTTP client renders it (`scheme://[userinfo@]host[:port]/path[?query][#fragment]`,
 * with `@`, `/`, `?` and `#` escaped inside each part); the result drops the userinfo, query and fragment.
 */
internal fun diagnosticUrl(url: String): String {
    val authorityStart = url.indexOf("://") + 3
    val pathStart = url.indexOf('/', authorityStart).let { if (it == -1) url.length else it }
    val hostStart = url.lastIndexOf('@', pathStart - 1).let { if (it < authorityStart) authorityStart else it + 1 }
    val pathEnd = url.indexOfAny(charArrayOf('?', '#'), pathStart).let { if (it == -1) url.length else it }
    return url.substring(0, authorityStart) + url.substring(hostStart, pathEnd)
}
