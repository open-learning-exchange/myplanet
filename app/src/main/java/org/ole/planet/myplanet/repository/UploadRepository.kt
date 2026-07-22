package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import retrofit2.Response

interface UploadRepository {
    suspend fun <T : Any> queryPending(config: UploadQueryContract<T>): List<T>
    suspend fun markUploaded(
        config: UploadUpdateContract,
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult>
    suspend fun postUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun putUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun fetchExistingDoc(url: String): Response<JsonObject>
}

data class UploadQueryContract<T : Any>(
    val queryType: UploadQueryType
)

data class UploadUpdateContract(
    val updateType: UploadUpdateType
)

enum class UploadQueryType {
    AdoptedSurveys,
    ExamResults,
    CompletedSubmissions,
}

enum class UploadUpdateType {
    Exams,
    Submissions,
}

data class UploadedItemResult(
    val localId: String,
    val remoteId: String,
    val remoteRev: String,
    val response: JsonObject
)
