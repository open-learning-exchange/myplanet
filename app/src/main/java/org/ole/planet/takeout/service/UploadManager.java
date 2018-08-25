package org.ole.planet.takeout.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Response;
import org.ole.planet.takeout.Data.realm_offlineActivities;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class UploadManager {
    private DatabaseService dbService;
    private Context context;
    private CouchDbProperties properties;
    private SharedPreferences sharedPreferences;
    private Realm mRealm;
    private static UploadManager instance;
    public static UploadManager getInstance() {
        if (instance == null) {
            instance = new UploadManager(MainApplication.context);
        }
        return instance;
    }



    public UploadManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
        mRealm = dbService.getRealmInstance();
    }

    public void uploadUserActivities() {
        properties = dbService.getClouchDbProperties("login_activities", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final RealmResults<realm_offlineActivities> activities = realm.where(realm_offlineActivities.class).findAll();
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                for (int i = 0; i < activities.size(); i++) {
                  Response r =   dbClient.post(realm_offlineActivities.serializeLoginActivities(activities.get(i)));
                    Utilities.log("Response " + r.getId());
                }
            }
        });
    }

}
