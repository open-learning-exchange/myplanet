package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;

import org.ole.planet.myplanet.Data.MyPlanet;
import org.ole.planet.myplanet.LoginActivity;
import org.ole.planet.myplanet.utilities.Utilities;

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

                if (response.body() != null && Utilities.getVersionCode(context) < response.body().getMinVersionCode()) {

                }

            }

            @Override
            public void onFailure(Call<MyPlanet> call, Throwable t) {

            }
        });
    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(String filePath);

        void onError(String msg);
    }
}
