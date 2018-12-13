package org.ole.planet.myplanet.datamanager;


import org.ole.planet.myplanet.Data.MyPlanet;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Streaming;
import retrofit2.http.Url;


public interface ApiInterface {
    @Streaming
    @GET
    Call<ResponseBody> downloadFile(@Header("Authorization") String header, @Url String fileUrl);

    @GET
    Call<MyPlanet> checkVersion(@Url String serverUrl);
}

