package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.data.NetworkResult

interface UploadRepository {
    suspend fun markUploaded(
        config: UploadUpdateContract,
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult>
    suspend fun postUpload(url: String, serializedData: JsonObject): NetworkResult<JsonObject>
    suspend fun postUploadArray(url: String, serializedData: JsonObject): NetworkResult<com.google.gson.JsonArray>
    suspend fun putUpload(url: String, serializedData: JsonObject): NetworkResult<JsonObject>
    suspend fun fetchExistingDoc(url: String): NetworkResult<JsonObject>
    suspend fun uploadAttachment(file: java.io.File, destinationFormat: String, id: String, rev: String, name: String): NetworkResult<JsonObject>
    suspend fun uploadResource(headerMap: Map<String, String>, url: String, file: java.io.File, mimeType: String): NetworkResult<JsonObject>
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
