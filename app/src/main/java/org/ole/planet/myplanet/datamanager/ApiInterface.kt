package org.ole.planet.myplanet.datamanager

import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.ole.planet.myplanet.model.ChatModel
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet
import retrofit2.Call
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
    fun getDocuments(@Header("Authorization") header: String?, @Url url: String?): Call<DocumentResponse>

    @GET
    fun getJsonObject(@Header("Authorization") header: String?, @Url url: String?): Call<JsonObject>

    @GET
    suspend fun getJsonObjectSuspended(@Header("Authorization") header: String?, @Url url: String?): Response<JsonObject>

    @POST
    fun findDocs(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @POST
    fun postDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @POST
    suspend fun postDocSuspend(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Response<JsonObject>

    @PUT
    fun uploadResource(@HeaderMap headerMap: Map<String, String>, @Url url: String?, @Body body: RequestBody?): Call<JsonObject>

    @PUT
    fun putDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @GET
    suspend fun checkVersion(@Url serverUrl: String?): Response<MyPlanet>

    @GET
    suspend fun getApkVersion(@Url url: String?): Response<ResponseBody>

    @GET
    fun healthAccess(@Url url: String?): Call<ResponseBody>

    @GET
    fun getChecksum(@Url url: String?): Call<ResponseBody>

    @GET
    fun isPlanetAvailable(@Url serverUrl: String?): Call<ResponseBody>

    @GET
    suspend fun isPlanetAvailableSuspend(@Url serverUrl: String?): Response<ResponseBody>

    @POST
    fun chatGpt(@Url url: String?, @Body requestBody: RequestBody?): Call<ChatModel>

    @GET
    fun checkAiProviders(@Url url: String?): Call<ResponseBody>

    @GET
    suspend fun getConfiguration(@Url url: String?): Response<JsonObject>
}
