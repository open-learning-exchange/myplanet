package org.ole.planet.myplanet.datamanager

import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface UploadApiInterface {
    @PUT
    suspend fun putDoc(
        @Header("Authorization") header: String?,
        @Header("Content-Type") c_type: String?,
        @Url url: String?,
        @Body s: JsonObject?
    ): Response<JsonObject>

    @POST
    suspend fun postDoc(
        @Header("Authorization") header: String?,
        @Header("Content-Type") c_type: String?,
        @Url url: String?,
        @Body s: JsonObject?
    ): Response<JsonObject>

    @POST
    suspend fun uploadResource(
        @HeaderMap headers: Map<String, String>,
        @Url url: String?,
        @Body body: RequestBody?
    ): Response<JsonObject>

    @GET
    suspend fun getJsonObject(
        @Header("Authorization") header: String?,
        @Url url: String?
    ): Response<JsonObject>

    @HEAD
    suspend fun getHeader(
        @Header("Authorization") header: String?,
        @Url url: String?
    ): Response<Void>

    @GET
    suspend fun getDocuments(
        @Header("Authorization") header: String?,
        @Url url: String?
    ): Response<JsonObject>

    @GET
    suspend fun checkVersion(
        @Url url: String?
    ): Response<ResponseBody>
}
