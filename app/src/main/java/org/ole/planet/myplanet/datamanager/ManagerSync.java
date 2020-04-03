package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.service.SyncManager;
import org.ole.planet.myplanet.service.TransactionSyncManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.List;

import io.realm.Realm;
import okhttp3.internal.Util;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;

public class ManagerSync {
    private static ManagerSync ourInstance;
    private SharedPreferences settings;
    private DatabaseService dbService;
    private Realm mRealm;

    private ManagerSync(Context context) {
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
        mRealm = dbService.getRealmInstance();
    }

    public static ManagerSync getInstance() {
        ourInstance = new ManagerSync(MainApplication.context);
        return ourInstance;
    }

    public void login(String userName, String password, SyncListener listener) {
        listener.onSyncStarted();
        Utilities.log(Utilities.getUrl() + "/org.couchdb.user:" +  userName);
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);

        apiInterface.getJsonObject("Basic " + Base64.encodeToString((userName + ":" +
                password).getBytes(), Base64.NO_WRAP), String.format("%s/_users/%s", Utilities.getUrl(), "org.couchdb.user:" + userName)).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonObject jsonDoc =  response.body();
                if (jsonDoc != null) {
                    AndroidDecrypter decrypt = new AndroidDecrypter();
                    String derivedKey = jsonDoc.get("derived_key").getAsString();
                    String salt = jsonDoc.get("salt").getAsString();
                    if (decrypt.AndroidDecrypter(userName, password, derivedKey, salt)) {
                        checkManagerAndInsert(jsonDoc, mRealm,listener);
                    }else{
                        listener.onSyncFailed("Name or password is incorrect.");
                    }
                } else {
                    listener.onSyncFailed("Name or password is incorrect.");
                }

            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                listener.onSyncFailed("Server not reachable.");
            }
        });

    }

    public void syncAdmin(){
        JsonObject object = new JsonObject();
        JsonObject selector = new JsonObject();
        selector.addProperty("isUserAdmin", true);
        object.add("selector",selector);
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        final retrofit2.Call<JsonObject> allDocs = apiInterface.findDocs(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/_users/_find", object);
        allDocs.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
               if (response.body()!=null){
                  JsonArray array =  JsonUtils.getJsonArray("docs", response.body());
                  if (array.size() > 0){
                      settings.edit().putString("user_admin", new Gson().toJson(array.get(0))).commit();
                  }
               }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void checkManagerAndInsert(JsonObject jsonDoc, Realm realm, SyncListener listener) {
        Utilities.log("Check manager and insert");

        if (isManager(jsonDoc)) {
            RealmUserModel.populateUsersTable(jsonDoc, realm, settings);
            listener.onSyncComplete();
        } else {
            listener.onSyncFailed("The user is not a manager.");
        }
    }

    private boolean isManager(JsonObject jsonDoc) {
        JsonArray roles = jsonDoc.get("roles").getAsJsonArray();
        boolean isManager = roles.toString().toLowerCase().contains("manager");
        return (jsonDoc.get("isUserAdmin").getAsBoolean() || isManager);
    }


}
