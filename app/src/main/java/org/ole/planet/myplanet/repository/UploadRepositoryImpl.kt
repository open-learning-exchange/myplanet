package org.ole.planet.myplanet.repository

import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
) : UploadRepository {

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
}
