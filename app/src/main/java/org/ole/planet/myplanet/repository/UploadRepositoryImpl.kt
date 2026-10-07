package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray as GsonJsonArray
import com.google.gson.JsonObject as GsonJsonObject
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx
import retrofit2.Response

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
) : UploadRepository {

    /**
     * The `*Json` methods are the kotlinx contract the upload services use. The Gson twins serve
     * the callers not migrated yet: they bridge the request with [toKotlinx] and the response
     * body with [toGson], reusing the raw HTTP response so status code and headers are unchanged.
     */
    private fun <K, T> Response<K>.bridgeTo(bodyMapper: (K) -> T): Response<T> {
        return if (isSuccessful) {
            Response.success(body()?.let(bodyMapper), raw())
        } else {
            Response.error(errorBody() ?: "".toResponseBody(null), raw())
        }
    }

    override suspend fun postJson(url: String, serializedData: JsonObject): Response<JsonObject> =
        apiInterface.postDoc(UrlUtils.header, "application/json", url, serializedData)

    override suspend fun postJsonArray(url: String, serializedData: JsonObject): Response<JsonArray> =
        apiInterface.postDocArray(UrlUtils.header, "application/json", url, serializedData)

    override suspend fun putJson(url: String, serializedData: JsonObject): Response<JsonObject> =
        apiInterface.putDoc(UrlUtils.header, "application/json", url, serializedData)

    override suspend fun fetchJson(url: String): Response<JsonObject> =
        apiInterface.getJsonObject(UrlUtils.header, url)

    override suspend fun uploadResourceJson(headerMap: Map<String, String>, url: String, body: RequestBody): Response<JsonObject> =
        apiInterface.uploadResource(headerMap, url, body)

    override suspend fun uploadAttachmentJson(
        file: File,
        destinationFormat: String,
        id: String,
        rev: String,
        name: String
    ): Response<JsonObject> {
        val (mimeType, body) = withContext(dispatcherProvider.io) {
            val type = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
            type to file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        }
        val url = String.format(destinationFormat, UrlUtils.getUrl(), id, name)

        return apiInterface.uploadResource(
            FileUploader.getHeaderMap(mimeType, rev),
            url,
            body
        )
    }

    override suspend fun postUpload(url: String, serializedData: GsonJsonObject): Response<GsonJsonObject> =
        postJson(url, serializedData.toKotlinx().jsonObject).bridgeTo { it.toGson() }

    override suspend fun postUploadArray(url: String, serializedData: GsonJsonObject): Response<GsonJsonArray> =
        postJsonArray(url, serializedData.toKotlinx().jsonObject).bridgeTo { it.toGson() }

    override suspend fun putUpload(url: String, serializedData: GsonJsonObject): Response<GsonJsonObject> =
        putJson(url, serializedData.toKotlinx().jsonObject).bridgeTo { it.toGson() }

    override suspend fun fetchExistingDoc(url: String): Response<GsonJsonObject> =
        fetchJson(url).bridgeTo { it.toGson() }

    override suspend fun uploadResource(headerMap: Map<String, String>, url: String, body: RequestBody): Response<GsonJsonObject> =
        uploadResourceJson(headerMap, url, body).bridgeTo { it.toGson() }

    override suspend fun uploadAttachment(
        file: File,
        destinationFormat: String,
        id: String,
        rev: String,
        name: String
    ): Response<GsonJsonObject> =
        uploadAttachmentJson(file, destinationFormat, id, rev, name).bridgeTo { it.toGson() }
}
