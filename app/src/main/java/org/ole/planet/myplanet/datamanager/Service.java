package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.ole.planet.myplanet.Data.MyPlanet;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import retrofit2.Call;
import retrofit2.Callback;

public class Service {
    private Context context;
    private SharedPreferences preferences;

    public Service(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void checkVersion(CheckVersionCallback callback, SharedPreferences settings) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.checkVersion(Utilities.getUpdateUrl(settings)).enqueue(new Callback<MyPlanet>() {
            @Override
            public void onResponse(Call<MyPlanet> call, retrofit2.Response<MyPlanet> response) {
                if (response.body() != null) {
                    preferences.edit().putString("versionDetail", new Gson().toJson(response.body()));
                    preferences.edit().commit();
                    checkForUpdate(response.body(), callback);
                } else {
                    callback.onError("Version not found", true);
                }
            }

            @Override
            public void onFailure(Call<MyPlanet> call, Throwable t) {
                callback.onError("Connection failed.", true);
            }
        });
    }

    private void checkForUpdate(MyPlanet body, CheckVersionCallback callback) {
        int currentVersion = VersionUtils.getVersionCode(context);
        if (currentVersion < body.getMinapkcode())
            callback.onUpdateAvailable(body.getApkpath(), false);
        else if (currentVersion < body.getLatestapkcode()) {
            callback.onUpdateAvailable(body.getApkpath(), true);
        } else {
            callback.onError("New version not available", false);
        }
    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(String filePath, boolean cancelable);

        void onCheckingVersion();

        void onError(String msg, boolean blockSync);

    }
}
