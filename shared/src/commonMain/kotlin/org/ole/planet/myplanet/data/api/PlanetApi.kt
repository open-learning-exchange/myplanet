package org.ole.planet.myplanet.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet

/**
 * Every HTTP call the app makes to a Planet/CouchDB server, with no transport types in its
 * signatures. A null [authorization] or [contentType] omits that header; [url] is absolute.
 * Text endpoints return the whole body as a String; [downloadFile] streams.
 */
interface PlanetApi {
    suspend fun downloadFile(
        authorization: String?,
        url: String?,
        range: String? = null,
        ifRange: String? = null
    ): ApiResponse<StreamBody>

    suspend fun getDocuments(authorization: String?, url: String?): ApiResponse<DocumentResponse>

    suspend fun getJsonObject(authorization: String?, url: String?): ApiResponse<JsonObject>

    suspend fun postDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject>

    suspend fun postDocArray(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonArray>

    suspend fun uploadResource(headers: Map<String, String>, url: String?, body: UploadBody?): ApiResponse<JsonObject>

    suspend fun putDoc(authorization: String?, contentType: String?, url: String?, body: JsonObject?): ApiResponse<JsonObject>

    suspend fun checkVersion(url: String?): ApiResponse<MyPlanet>

    suspend fun getApkVersion(url: String?): ApiResponse<String>

    suspend fun healthAccess(url: String?): ApiResponse<String>

    suspend fun getChecksum(url: String?): ApiResponse<String>

    suspend fun isPlanetAvailable(url: String?): ApiResponse<String>

    suspend fun chatGpt(url: String?, body: UploadBody?): ApiResponse<ChatResponse>

    suspend fun checkAiProviders(url: String?): ApiResponse<String>

    suspend fun getConfiguration(url: String?): ApiResponse<JsonObject>
}
