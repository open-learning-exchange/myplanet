package org.ole.planet.myplanet.data.api

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSource
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet
import retrofit2.Response

/**
 * [PlanetApi] over the Retrofit [ApiInterface]. Each call delegates to the matching Retrofit
 * endpoint unchanged, then wraps the [Response] without reading anything early: a text body
 * is decoded, and an error body turned into text, only when the caller asks for it, and a
 * streaming download body is handed over unread.
 */
class RetrofitPlanetApi(
    private val apiInterface: ApiInterface
) : PlanetApi {

    override suspend fun downloadFile(authorization: String?, url: String?, range: String?, ifRange: String?): ApiResponse<StreamBody> =
        apiInterface.downloadFile(authorization, url, range, ifRange).toApiResponse { body -> OkHttpStreamBody(body) }

    override suspend fun getDocuments(authorization: String?, url: String?): ApiResponse<DocumentResponse> =
        apiInterface.getDocuments(authorization, url).toApiResponse()

    override suspend fun getJsonObject(authorization: String?, url: String?): ApiResponse<JsonObject> =
        apiInterface.getJsonObject(authorization, url).toApiResponse()

    override suspend fun postDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject> =
        apiInterface.postDoc(authorization, contentType, url, body).toApiResponse()

    override suspend fun postDocArray(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonArray> =
        apiInterface.postDocArray(authorization, contentType, url, body).toApiResponse()

    override suspend fun uploadResource(headers: Map<String, String>, url: String?, body: UploadBody?): ApiResponse<JsonObject> =
        apiInterface.uploadResource(headers, url, body?.toRequestBody()).toApiResponse()

    override suspend fun putDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject> =
        apiInterface.putDoc(authorization, contentType, url, body).toApiResponse()

    override suspend fun checkVersion(url: String?): ApiResponse<MyPlanet> =
        apiInterface.checkVersion(url).toApiResponse()

    override suspend fun getApkVersion(url: String?): ApiResponse<String> =
        apiInterface.getApkVersion(url).toTextResponse()

    override suspend fun healthAccess(url: String?): ApiResponse<String> =
        apiInterface.healthAccess(url).toTextResponse()

    override suspend fun getChecksum(url: String?): ApiResponse<String> =
        apiInterface.getChecksum(url).toTextResponse()

    override suspend fun isPlanetAvailable(url: String?): ApiResponse<String> =
        apiInterface.isPlanetAvailable(url).toTextResponse()

    override suspend fun chatGpt(url: String?, body: UploadBody?): ApiResponse<ChatResponse> =
        apiInterface.chatGpt(url, body?.toRequestBody()).toApiResponse()

    override suspend fun checkAiProviders(url: String?): ApiResponse<String> =
        apiInterface.checkAiProviders(url).toTextResponse()

    override suspend fun getConfiguration(url: String?): ApiResponse<JsonObject> =
        apiInterface.getConfiguration(url).toApiResponse()

    private class OkHttpStreamBody(private val body: ResponseBody) : StreamBody {
        override val contentLength: Long get() = body.contentLength()

        override fun source(): BufferedSource = body.source()

        override fun close() = body.close()
    }

    private companion object {
        fun UploadBody.toRequestBody(): RequestBody = when (this) {
            is UploadBody.FileContent -> File(path).asRequestBody(contentType?.toMediaTypeOrNull())
            is UploadBody.TextContent -> text.toRequestBody(contentType?.toMediaTypeOrNull())
        }

        // Retrofit has already buffered a non-streaming ResponseBody; string() only decodes it.
        fun Response<ResponseBody>.toTextResponse(): ApiResponse<String> = toApiResponse { it.string() }

        fun <T> Response<T>.toApiResponse(): ApiResponse<T> = toApiResponse { it }

        fun <T, R> Response<T>.toApiResponse(mapBody: (T) -> R): ApiResponse<R> {
            val raw = raw()
            return ApiResponse(
                code = code(),
                message = message(),
                headers = headers().toList(),
                requestUrl = raw.request.url.toString(),
                bodyReader = { body()?.let(mapBody) },
                errorBodyReader = { errorBody()?.string() },
            )
        }
    }
}
