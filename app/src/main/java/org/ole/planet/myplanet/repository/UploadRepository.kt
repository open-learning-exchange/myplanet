package org.ole.planet.myplanet.repository

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.Response

interface UploadRepository {
    suspend fun postJson(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun postJsonArray(url: String, serializedData: JsonObject): Response<JsonArray>
    suspend fun putJson(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun fetchJson(url: String): Response<JsonObject>
    suspend fun uploadAttachmentJson(file: File, destinationFormat: String, id: String, rev: String, name: String): Response<JsonObject>
    suspend fun uploadResourceJson(headerMap: Map<String, String>, url: String, body: RequestBody): Response<JsonObject>
}

data class UploadedItemResult(
    val localId: String,
    val remoteId: String,
    val remoteRev: String,
    val response: JsonObject
)
