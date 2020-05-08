package org.ole.planet.myplanet.datamanager;


import com.google.gson.JsonObject;

import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.MyPlanet;

import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Streaming;
import retrofit2.http.Url;


public interface ApiInterface {
    @Streaming
    @GET
    Call<ResponseBody> downloadFile(@Header("Authorization") String header, @Url String fileUrl);

    @GET
    Call<DocumentResponse> getDocuments(@Header("Authorization") String header, @Url String url);

    @GET
    Call<JsonObject> getJsonObject(@Header("Authorization") String header, @Url String url);

    @POST
    Call<JsonObject> findDocs(@Header("Authorization") String header, @Header("Content-Type") String c, @Url String url, @Body JsonObject s);

    @POST
    Call<JsonObject> postDoc(@Header("Authorization") String header, @Header("Content-Type") String c, @Url String url, @Body JsonObject s);

    @PUT
    Call<JsonObject> uploadResource(@HeaderMap Map<String, String> headerMap, @Url String url, @Body RequestBody body);

    @PUT
    Call<JsonObject> putDoc(@Header("Authorization") String header, @Header("Content-Type") String c, @Url String url, @Body JsonObject s);

    @GET
    Call<MyPlanet> checkVersion(@Url String serverUrl);


    @GET
    Call<ResponseBody> getApkVersion(@Url String url);


    @GET
    Call<ResponseBody> healthAccess(@Url String url);


    @GET
    Call<ResponseBody> getChecksum(@Url String url);

    @GET
    Call<ResponseBody> isPlanetAvailable(@Url String serverUrl);
}

