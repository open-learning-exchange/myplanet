package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import retrofit2.Response

interface UploadRepository {
    suspend fun markUploaded(
        config: UploadUpdateContract,
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult>
    suspend fun postUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun postUploadArray(url: String, serializedData: JsonObject): Response<com.google.gson.JsonArray>
    suspend fun putUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun fetchExistingDoc(url: String): Response<JsonObject>
    suspend fun uploadAttachment(file: java.io.File, destinationFormat: String, id: String, rev: String, name: String): Response<JsonObject>
    suspend fun uploadResource(headerMap: Map<String, String>, url: String, body: okhttp3.RequestBody): Response<JsonObject>
}

data class UploadUpdateContract(
    val updateType: UploadUpdateType
)

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
