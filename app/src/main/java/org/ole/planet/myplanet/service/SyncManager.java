package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmResourceActivity;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import retrofit2.Response;

public class SyncManager {
    static final String PREFS_NAME = "OLE_PLANET";
    private static SyncManager ourInstance;
    private SharedPreferences settings;
    private Realm mRealm;
    private Context context;
    private boolean isSyncing = false;
    private String[] stringArray = new String[4];
    private Rows shelfDoc;
    private SyncListener listener;
    private DatabaseService dbService;
    private UserProfileDbHandler userProfileDbHandler;

    private SyncManager(Context context) {
        this.context = context;
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
        userProfileDbHandler = new UserProfileDbHandler(context);
    }

    public static SyncManager getInstance() {
        //   if (ourInstance == null) {
        ourInstance = new SyncManager(MainApplication.context);
        // }
        return ourInstance;
    }

    public void start(SyncListener listener) {
        this.listener = listener;
        if (!isSyncing) {
            if (listener != null) {
                listener.onSyncStarted();
            }
            authenticateAndSync();
        } else {
            Utilities.log("Already Syncing...");
        }
    }

    public void destroy() {
        NotificationUtil.cancel(context, 111);
        isSyncing = false;
        ourInstance = null;
        settings.edit().putLong("LastSync", new Date().getTime()).commit();
        if (listener != null) {
            listener.onSyncComplete();
        }
        try {
            mRealm.close();
            td.stop();
        } catch (Exception e) {
        }
    }

    Thread td;

    private void authenticateAndSync() {
        td = new Thread(() -> {
            if (TransactionSyncManager.authenticate()) {
                startSync();
            } else {
                handleException("Invalid name or password");
                destroy();
            }
        });
        td.start();
    }

    private void startSync() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                settings.edit().putString("LastWifiSSID", wifiInfo.getSSID()).commit();
            }
            isSyncing = true;
            NotificationUtil.create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...");
            mRealm = dbService.getRealmInstance();
            TransactionSyncManager.syncDb(mRealm, "tablet_users");
            TransactionSyncManager.syncDb(mRealm, "courses");
            TransactionSyncManager.syncDb(mRealm, "exams");
            resourceTransactionSync();
            TransactionSyncManager.syncDb(mRealm, "ratings");
            TransactionSyncManager.syncDb(mRealm, "achievements");
            TransactionSyncManager.syncDb(mRealm, "tags");
            TransactionSyncManager.syncDb(mRealm, "submissions");
            myLibraryTransactionSync();
            TransactionSyncManager.syncDb(mRealm, "login_activities");
            RealmResourceActivity.onSynced(mRealm, settings);
        } catch (Exception err) {
            handleException(err.getMessage());
        } finally {
            destroy();
        }
    }

    private void handleException(String message) {
        if (listener != null) {
            isSyncing = false;
            MainApplication.syncFailedCount++;
            listener.onSyncFailed(message);
        }
    }


    public void resourceTransactionSync() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransaction(realm -> {
            try {
                syncResource(apiInterface);
            } catch (IOException e) {
            }
        });
    }

    private void syncResource(ApiInterface dbClient) throws IOException {
        int skip = 0;
        int limit = 1000;
        List<String> newIds = new ArrayList<>();
        while (true) {
            JsonObject object = new JsonObject();
            object.add("selector", new JsonObject());
            object.addProperty("limit", limit);
            object.addProperty("skip", skip);
            Utilities.log("Url " + Utilities.getUrl() + "/resources/_find");
            final retrofit2.Call<JsonObject> allDocs = dbClient.findDocs(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/resources/_find", object);
            Response<JsonObject> a = allDocs.execute();
            List<String> ids = RealmMyLibrary.save(JsonUtils.getJsonArray("docs", a.body()), mRealm);
            newIds.addAll(ids);
            if (a.body().size() < limit) {
                break;
            } else {
                skip = skip + limit;
            }
        }
        RealmMyLibrary.removeDeletedResource(newIds, mRealm);
    }


    private void myLibraryTransactionSync() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransaction(realm -> {
            try {
                DocumentResponse res = apiInterface.getDocuments(Utilities.getHeader(), Utilities.getUrl() + "/shelf/_all_docs").execute().body();
                for (int i = 0; i < res.getRows().size(); i++) {
                    shelfDoc = res.getRows().get(i);
                    populateShelfItems(apiInterface, realm);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }


    private void populateShelfItems(ApiInterface apiInterface, Realm mRealm) {
        try {
            this.mRealm = mRealm;
            JsonObject jsonDoc = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + shelfDoc.getId()).execute().body();
            for (int i = 0; i < Constants.shelfDataList.size(); i++) {
                Constants.ShelfData shelfData = Constants.shelfDataList.get(i);
                JsonArray array = JsonUtils.getJsonArray(shelfData.key, jsonDoc);
                memberShelfData(array, shelfData);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void memberShelfData(JsonArray array, Constants.ShelfData shelfData) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type);
            check(array, shelfData.aClass);
        }
    }

    private void triggerInsert(String categroryId, String categoryDBName) {
        stringArray[0] = shelfDoc.getId();
        stringArray[1] = categroryId;
        stringArray[2] = categoryDBName;
    }


    private void check(JsonArray array_categoryIds, Class aClass) {
        for (int x = 0; x < array_categoryIds.size(); x++) {
            if (array_categoryIds.get(x) instanceof JsonNull) {
                continue;
            }
            checkEmptyAndSave(aClass, x, array_categoryIds);
        }
    }

    private void checkEmptyAndSave(Class aClass, int x, JsonArray array_categoryIds) {
        List db_Categrory = null;

        if (aClass == RealmMyLibrary.class || aClass == RealmMyCourse.class) {
            db_Categrory =
                    RealmMyLibrary.getShelfItem(stringArray[0], mRealm.where(aClass)
                            .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                            .findAll(), aClass);
        } else {
            db_Categrory = mRealm.where(aClass)
                    .contains("userId", stringArray[0])
                    .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                    .findAll();
        }
        if (db_Categrory.isEmpty()) {
            validateDocument(array_categoryIds, x);
        } else {
            Log.e("DATA", " Data already saved for -- " + stringArray[0] + " " + array_categoryIds.get(x).getAsString());
        }
    }

    private void validateDocument(JsonArray array_categoryIds, int x) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        try {
            JsonObject resourceDoc = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/" + stringArray[2] + "/" + array_categoryIds.get(x).getAsString()).execute().body();
            if (resourceDoc != null)
                triggerInsert(stringArray, array_categoryIds, x, resourceDoc);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void triggerInsert(String[] stringArray, JsonArray array_categoryIds,
                               int x, JsonObject resourceDoc) {
        switch (stringArray[2]) {
            case "resources":
                RealmMyLibrary.insertMyLibrary(stringArray[0], resourceDoc, mRealm);
                break;
            case "meetups":
                RealmMeetup.insertMyMeetups(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
            case "courses":
                RealmMyCourse.insertMyCourses(stringArray[0], resourceDoc, mRealm);
                break;
            case "teams":
                RealmMyTeam.insertMyTeams(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
        }
    }
}