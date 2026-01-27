package org.ole.planet.myplanet.data.api

import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.ole.planet.myplanet.model.ChatModel
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiInterface {
    @Streaming
    @GET
    suspend fun downloadFile(@Header("Authorization") header: String?, @Url fileUrl: String?): Response<ResponseBody>

    @GET
    suspend fun getDocuments(@Header("Authorization") header: String?, @Url url: String?): Response<DocumentResponse>

    @GET
    suspend fun getJsonObject(@Header("Authorization") header: String?, @Url url: String?): Response<JsonObject>

    @POST
    suspend fun findDocs(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Response<JsonObject>

    @POST
    suspend fun postDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Response<JsonObject>

    @PUT
    suspend fun uploadResource(@HeaderMap headerMap: Map<String, String>, @Url url: String?, @Body body: RequestBody?): Response<JsonObject>

    @PUT
    suspend fun putDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Response<JsonObject>

    @GET
    suspend fun checkVersion(@Url serverUrl: String?): Response<MyPlanet>

    @GET
    suspend fun getApkVersion(@Url url: String?): Response<ResponseBody>

    @GET
    suspend fun healthAccess(@Url url: String?): Response<ResponseBody>

    @GET
    suspend fun getChecksum(@Url url: String?): Response<ResponseBody>

    @GET
    suspend fun isPlanetAvailable(@Url serverUrl: String?): Response<ResponseBody>

    @POST
    suspend fun chatGpt(@Url url: String?, @Body requestBody: RequestBody?): Response<ChatModel>

    @GET
    suspend fun checkAiProviders(@Url url: String?): Response<ResponseBody>

    @GET
    suspend fun getConfiguration(@Url url: String?): Response<JsonObject>
}
