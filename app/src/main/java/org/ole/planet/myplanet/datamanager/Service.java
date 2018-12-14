package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;

import com.google.gson.Gson;

import org.ole.planet.myplanet.Data.MyPlanet;
import org.ole.planet.myplanet.LoginActivity;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;

public class Service {
    Context context;

    public Service(Context context) {
        this.context = context;
    }

    public void checkVersion(CheckVersionCallback callback, SharedPreferences settings) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.checkVersion(Utilities.getUpdateUrl(settings)).enqueue(new Callback<MyPlanet>() {
            @Override
            public void onResponse(Call<MyPlanet> call, retrofit2.Response<MyPlanet> response) {
                Utilities.log("Response  " + new Gson().toJson(response.body()));
                if (response.body() != null && !("v" + VersionUtils.getVersionName(context)).equals(response.body().getLatestapk())) {
//                if (response.body() != null) {
                    callback.onUpdateAvailable(response.body().getApkpath());
                } else {
                    callback.onError("Version not available");
                }
            }

            @Override
            public void onFailure(Call<MyPlanet> call, Throwable t) {
                callback.onError("Connection failed.");
            }
        });
    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(String filePath);

        void onCheckingVersion();

        void onError(String msg);
    }
}
