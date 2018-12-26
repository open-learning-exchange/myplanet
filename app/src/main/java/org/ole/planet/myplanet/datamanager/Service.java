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
                int currentVersion = VersionUtils.getVersionCode(context);
                if (response.body() != null) {
                    if (currentVersion < response.body().getMinapkcode())
                        callback.onUpdateAvailable(response.body().getApkpath(), false);
                    else if (currentVersion < response.body().getLatestapkcode()) {
                        callback.onUpdateAvailable(response.body().getApkpath(), true);
                    }
                } else {
                    callback.onError("New version not available");
                }
            }

            @Override
            public void onFailure(Call<MyPlanet> call, Throwable t) {
                callback.onError("Connection failed.");
            }
        });
    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(String filePath, boolean cancelable);

        void onCheckingVersion();

        void onError(String msg);
    }
}
