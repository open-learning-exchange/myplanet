package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.data.NetworkResult
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
     * repository's own contract is Gson-typed and Retrofit-free, so callers never see
     * retrofit2.Response. Retries stay owned by the caller (RetryQueue, UploadCoordinator's
     * 409 recovery) - this never retries on its own.
     */
    private suspend fun <K, T> apiCall(mapper: (K) -> T, block: suspend () -> Response<K>): NetworkResult<T> {
        return try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                NetworkResult.Success(mapper(body))
            } else {
                NetworkResult.Error(response.code(), null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Exception(e)
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
    ): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) {
            apiInterface.postDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun postUploadArray(
        url: String,
        serializedData: JsonObject
    ): NetworkResult<com.google.gson.JsonArray> {
        return apiCall({ it.toGson() }) {
            apiInterface.postDocArray(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun putUpload(
        url: String,
        serializedData: JsonObject
    ): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) {
            apiInterface.putDoc(UrlUtils.header, "application/json", url, serializedData.toKotlinx().jsonObject)
        }
    }

    override suspend fun fetchExistingDoc(url: String): NetworkResult<JsonObject> {
        return apiCall({ it.toGson() }) { apiInterface.getJsonObject(UrlUtils.header, url) }
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
        file: File,
        mimeType: String
    ): NetworkResult<JsonObject> {
        val body = withContext(dispatcherProvider.io) {
            file.asRequestBody(mimeType.toMediaTypeOrNull())
        }
        return apiCall({ it.toGson() }) { apiInterface.uploadResource(headerMap, url, body) }
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
            type to file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        }
        val url = String.format(destinationFormat, UrlUtils.getUrl(), id, name)

        return apiCall({ it.toGson() }) {
            apiInterface.uploadResource(
                FileUploader.getHeaderMap(mimeType, rev),
                url,
                body
            )
        }
    }
}
