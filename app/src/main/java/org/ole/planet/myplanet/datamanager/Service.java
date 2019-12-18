package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

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
import retrofit2.Response;

public class Service {
    private Context context;
    private SharedPreferences preferences;

    public Service(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void checkVersion(CheckVersionCallback callback, SharedPreferences settings) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        if (settings.getString("couchdbURL", "").isEmpty()) {
            callback.onError("Config not awailable.", true);
            return;
        }
        Utilities.log("Check version");
        retrofitInterface.checkVersion(Utilities.getUpdateUrl(settings)).enqueue(new Callback<MyPlanet>() {
            @Override
            public void onResponse(Call<MyPlanet> call, retrofit2.Response<MyPlanet> response) {
                preferences.edit().putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context)).commit();
                if (response.body() != null) {
                    MyPlanet p = response.body();
                    preferences.edit().putString("versionDetail", new Gson().toJson(response.body())).commit();
                    retrofitInterface.getApkVersion(Utilities.getApkVersionUrl(settings)).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            String responses = null;
                            try {
                                responses = new Gson().fromJson(response.body().string(), String.class);
                                if (responses.isEmpty()) {
                                    callback.onError("Planet up to date", false);
                                    return;
                                }
                                String vsn = responses.replaceAll("v", "");
                                vsn = vsn.replaceAll("\\.", "");
                                Log.e("Response ", vsn);
                                int apkVersion = Integer.parseInt(vsn.startsWith("0") ? vsn.replace("0", "") : vsn);
                                int currentVersion = VersionUtils.getVersionCode(context);
                                Utilities.log(p.getLatestapk() + " " + currentVersion + " " + p.getMinapkcode());
                                if (apkVersion > currentVersion ) {
                                    callback.onUpdateAvailable(p, false);
                                    return;
                                }
                                if (currentVersion < p.getMinapkcode() &&  apkVersion <= p.getMinapkcode()) {
                                    callback.onUpdateAvailable(p, true);
                                } else {
                                    callback.onError("Planet up to date", false);
                                }

                            } catch (Exception e) {
                                Log.e("Error", e.getLocalizedMessage());
                                callback.onError("New apk version required  but not found on server - Contact admin", false);
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {

                        }
                    });

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
                } else {
                    callback.notAvailable();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.notAvailable();
            }
        });
    }

//    private void checkForUpdate(MyPlanet body, CheckVersionCallback callback) {
//        int currentVersion = VersionUtils.getVersionCode(context);
//        if (currentVersion < body.getMinapkcode())
//            callback.onUpdateAvailable(body, false);
//        else if (currentVersion < body.getLatestapkcode()) {
//            callback.onUpdateAvailable(body, true);
//        } else {
//            callback.onError("Planet up to date", false);
//        }
//    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(MyPlanet info, boolean cancelable);

        void onCheckingVersion();

        void onError(String msg, boolean blockSync);
    }

    public interface PlanetAvailableListener {
        void isAvailable();

        void notAvailable();
    }
}
