package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx
import retrofit2.Response

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val examDao: ExamDao,
    private val submissionDao: SubmissionDao,
    private val dispatcherProvider: DispatcherProvider,
) : UploadRepository {

    /**
     * ApiInterface's raw doc endpoints are kotlinx-typed at the network boundary; this
     * repository's own contract stays Gson-typed since UploadCoordinator/TeamsUploader/
     * AchievementUploader/etc. all consume it that way. Bridges the body only, reusing the
     * original raw HTTP response so status code/headers are unaffected.
     */
    private fun <K, T> Response<K>.bridgeTo(bodyMapper: (K) -> T): Response<T> {
        return if (isSuccessful) {
            Response.success(body()?.let(bodyMapper), raw())
        } else {
            Response.error(errorBody() ?: "".toResponseBody(null), raw())
        }
    }

    override suspend fun markUploaded(
        config: UploadUpdateContract,
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult> {
        return when (config.updateType) {
            UploadUpdateType.Exams -> markExamsUploaded(succeeded)
            UploadUpdateType.Submissions -> succeeded.filter { result ->
                submissionDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    }

    override suspend fun postUpload(
        url: String,
        serializedData: JsonObject
    ): Response<JsonObject> {
        return apiInterface.postDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
            .bridgeTo { it.toGson() }
    }
    override suspend fun postUploadArray(
        url: String,
        serializedData: JsonObject
    ): Response<com.google.gson.JsonArray> {
        return apiInterface.postDocArray(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
            .bridgeTo { it.toGson() }
    }

    override suspend fun putUpload(
        url: String,
        serializedData: JsonObject
    ): Response<JsonObject> {
        return apiInterface.putDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
            .bridgeTo { it.toGson() }
    }

    override suspend fun fetchExistingDoc(url: String): Response<JsonObject> {
        return apiInterface.getJsonObject(UrlUtils.header, url).bridgeTo { it.toGson() }
    }

    private suspend fun markExamsUploaded(
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult> {
        if (succeeded.isEmpty()) return emptyList()
        val existing = examDao.getByIds(succeeded.map { it.localId }).associateBy { it.id }
        val updated = ArrayList<StepExam>(succeeded.size)
        val failed = ArrayList<UploadedItemResult>(succeeded.size)

        succeeded.forEach { result ->
            val exam = existing[result.localId]
            if (exam == null) {
                failed += result
            } else {
                exam._rev = result.remoteRev
                updated += exam
            }
        }

        if (updated.isNotEmpty()) {
            examDao.upsertAll(updated)
        }

        return failed
    }

    override suspend fun uploadResource(
        headerMap: Map<String, String>,
        url: String,
        body: okhttp3.RequestBody
    ): Response<JsonObject> {
        return apiInterface.uploadResource(headerMap, url, body).bridgeTo { it.toGson() }
    }

    override suspend fun uploadAttachment(
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
        ).bridgeTo { it.toGson() }
    }
}
