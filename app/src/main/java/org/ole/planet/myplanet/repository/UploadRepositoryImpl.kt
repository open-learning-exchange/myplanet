package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.data.api.ApiResponse
import org.ole.planet.myplanet.data.api.PlanetApi
import org.ole.planet.myplanet.data.api.UploadBody
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val planetApi: PlanetApi,
    private val dispatcherProvider: DispatcherProvider,
) : UploadRepository {

    /**
     * PlanetApi's raw doc endpoints are kotlinx-typed at the network boundary; this
     * repository's own contract is Gson-typed and transport-free, so callers never see
     * ApiResponse. Retries stay owned by the caller (RetryQueue, UploadCoordinator's
     * 409 recovery) - this never retries on its own.
     */
    private suspend fun <K, T> apiCall(mapper: (K) -> T, block: suspend () -> ApiResponse<K>): NetworkResult<T> {
        return try {
            val response = block()
            val body = response.body
            if (response.isSuccessful && body != null) {
                NetworkResult.Success(mapper(body))
            } else {
                NetworkResult.Error(response.code, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    override suspend fun postUpload(
        url: String,
        serializedData: JsonObject
    ): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) {
            planetApi.postDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun postUploadArray(
        url: String,
        serializedData: JsonObject
    ): NetworkResult<com.google.gson.JsonArray> {
        return apiCall({ it.toGson() }) {
            planetApi.postDocArray(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun putUpload(
        url: String,
        serializedData: JsonObject
    ): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) {
            planetApi.putDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun fetchExistingDoc(url: String): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) { planetApi.getJsonObject(UrlUtils.header, url) }
    }

    override suspend fun uploadResource(
        headerMap: Map<String, String>,
        url: String,
        file: File,
        mimeType: String
    ): NetworkResult<JsonObject> {
        val body = UploadBody.FileContent(file.path, mimeType)
        return apiCall({ it.toGson() }) { planetApi.uploadResource(headerMap, url, body) }
    }

    override suspend fun uploadAttachment(
        file: File,
        destinationFormat: String,
        id: String,
        rev: String,
        name: String
    ): NetworkResult<JsonObject> {
        val (mimeType, body) = withContext(dispatcherProvider.io) {
            val type = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
            type to UploadBody.FileContent(file.path, "application/octet-stream")
        }
        val url = String.format(destinationFormat, UrlUtils.getUrl(), id, name)

        return apiCall({ it.toGson() }) {
            planetApi.uploadResource(
                FileUploader.getHeaderMap(mimeType, rev),
                url,
                body
            )
        }
    }
}
