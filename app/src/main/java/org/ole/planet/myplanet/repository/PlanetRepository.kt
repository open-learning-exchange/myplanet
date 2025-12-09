package org.ole.planet.myplanet.repository

import javax.inject.Inject
import okhttp3.ResponseBody
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.NetworkResult
import retrofit2.Response

interface PlanetRepository {
    suspend fun healthAccess(url: String): NetworkResult<String>
}

class PlanetRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface
) : PlanetRepository {

    override suspend fun healthAccess(url: String): NetworkResult<String> {
        return try {
            val response = apiInterface.healthAccessSuspend(url)
            handleResponse(response)
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun handleResponse(response: Response<ResponseBody>): NetworkResult<String> {
        return if (response.isSuccessful && response.code() == 200) {
            NetworkResult.Success("Success")
        } else {
            val msg = when (response.code()) {
                401 -> "Unauthorized - Invalid credentials"
                404 -> "Server endpoint not found"
                500 -> "Server internal error"
                502 -> "Bad gateway - Server unavailable"
                503 -> "Service temporarily unavailable"
                504 -> "Gateway timeout"
                else -> "Server error: ${response.code()}"
            }
            NetworkResult.Error(response.code(), msg)
        }
    }

    private fun handleException(t: Throwable): NetworkResult<String> {
        val errorMsg = when (t) {
            is java.net.UnknownHostException -> "Server not reachable"
            is java.net.SocketTimeoutException -> "Connection timeout"
            is java.net.ConnectException -> "Unable to connect to server"
            is java.io.IOException -> "Network connection error"
            else -> "Network error: ${t.localizedMessage ?: "Unknown error"}"
        }
        return NetworkResult.Error(null, errorMsg)
    }
}
