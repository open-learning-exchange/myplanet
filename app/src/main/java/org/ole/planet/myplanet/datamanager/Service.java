package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.io.IOException;

import okhttp3.ResponseBody;
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
        if (settings.getString("couchdbURL", "").isEmpty()){
            callback.onError("Config not awailable.", true);
            return;
        }
        retrofitInterface.checkVersion(Utilities.getUpdateUrl(settings)).enqueue(new Callback<MyPlanet>() {
            @Override
            public void onResponse(Call<MyPlanet> call, retrofit2.Response<MyPlanet> response) {
                preferences.edit().putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context)).commit();
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
                t.printStackTrace();
                callback.onError("Connection failed.", true);
            }
        });
    }

    public void isPlanetAvailable(PlanetAvailableListener callback) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.isPlanetAvailable(Utilities.getUpdateUrl(preferences)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (callback != null && response.code() == 200) {
                    callback.isAvailable();
                }else{
                    callback.notAvailable();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.notAvailable();
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

    public interface PlanetAvailableListener {
        void isAvailable();

        void notAvailable();
    }
}
