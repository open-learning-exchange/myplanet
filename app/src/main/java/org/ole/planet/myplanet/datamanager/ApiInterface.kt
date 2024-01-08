package org.ole.planet.myplanet.datamanager

import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.ui.chat.ChatModel
import retrofit2.Call
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
    fun downloadFile(@Header("Authorization") header: String?, @Url fileUrl: String?): Call<ResponseBody>

    @GET
    fun getDocuments(@Header("Authorization") header: String?, @Url url: String?): Call<DocumentResponse>

    @GET
    fun getJsonObject(@Header("Authorization") header: String?, @Url url: String?): Call<JsonObject>

    @POST
    fun findDocs(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @POST
    fun postDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @PUT
    fun uploadResource(@HeaderMap headerMap: Map<String, String>, @Url url: String?, @Body body: RequestBody?): Call<JsonObject>

    @PUT
    fun putDoc(@Header("Authorization") header: String?, @Header("Content-Type") c: String?, @Url url: String?, @Body s: JsonObject?): Call<JsonObject>

    @GET
    fun checkVersion(@Url serverUrl: String?): Call<MyPlanet>

    @GET
    fun getApkVersion(@Url url: String?): Call<ResponseBody>

    @GET
    fun healthAccess(@Url url: String?): Call<ResponseBody>

    @GET
    fun getChecksum(@Url url: String?): Call<ResponseBody>

    @GET
    fun isPlanetAvailable(@Url serverUrl: String?): Call<ResponseBody>

    @POST
    fun chatGpt(@Url url: String?, @Body requestBody: RequestBody?): Call<ChatModel>
}
