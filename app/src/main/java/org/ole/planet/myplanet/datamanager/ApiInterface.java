package org.ole.planet.myplanet.datamanager;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.Document;
import org.ole.planet.myplanet.Data.DocumentResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;
import retrofit2.http.Url;


public interface ApiInterface {
    @Streaming
    @GET
    Call<ResponseBody> downloadFile(@Header("Authorization") String header, @Url String fileUrl);

    @GET
    Call<DocumentResponse> getDocuments(@Header("Authorization") String header, @Url String url);


    @GET
    Call<JsonObject> getJsonObject(@Header("Authorization") String header,@Url String url);


    @POST
    Call<JsonObject> findDocs(@Header("Authorization") String header, @Header("Content-Type")String c,@Url String url, @Body JsonObject s);
}

