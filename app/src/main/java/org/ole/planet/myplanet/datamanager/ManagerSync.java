package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.service.SyncManager;
import org.ole.planet.myplanet.service.TransactionSyncManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;

import io.realm.Realm;

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
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            try {
                JsonObject jsonDoc = apiInterface.getJsonObject(Utilities.getHeader(), String.format("%s/_users/%s", Utilities.getUrl(), "org.couchdb.user:" + userName)).execute().body();
                if (jsonDoc != null) {
                    AndroidDecrypter decrypt = new AndroidDecrypter();
                    String derivedKey = jsonDoc.get("derived_key").getAsString();
                    String salt = jsonDoc.get("salt").getAsString();
                    if (decrypt.AndroidDecrypter(userName, password, derivedKey, salt)) {
                        checkManagerAndInsert(jsonDoc, realm,listener);
                    }else{
                        listener.onSyncFailed("Name or password is incorrect.");
                    }
                } else {
                    listener.onSyncFailed("Name or password is incorrect.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void checkManagerAndInsert(JsonObject jsonDoc, Realm realm, SyncListener listener) {
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
