package org.ole.planet.takeout.datamanager;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * Created by rowsun on 5/9/17.
 */

public interface ApiInterface {
    //@Headers("Authorization:Basic dmk6aXY=")
    @GET
    Call<ResponseBody> downloadFile(@Header("Authorization") String header, @Url String fileUrl);

}


