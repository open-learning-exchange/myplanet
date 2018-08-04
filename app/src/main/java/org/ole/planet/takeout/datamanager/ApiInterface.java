package org.ole.planet.takeout.datamanager;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Streaming;
import retrofit2.http.Url;


public interface ApiInterface {
    @GET
    @Streaming
    Call<ResponseBody> downloadFile(@Header("Authorization") String header, @Url String fileUrl);

}

