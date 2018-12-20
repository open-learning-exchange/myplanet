package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.Call;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.DocumentResponse;
import org.ole.planet.myplanet.Data.Rows;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_myTeams;
import org.ole.planet.myplanet.Data.realm_resourceActivities;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLException;

import io.realm.Realm;
import io.realm.RealmResults;
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
        if (ourInstance == null) {
            ourInstance = new SyncManager(MainApplication.context);
        }
        return ourInstance;
    }

    public void start(SyncListener listener) {
        this.listener = listener;
        if (!isSyncing) {
            if (listener != null) {
                listener.onSyncStarted();
            }
            syncDatabase();
        } else {
            Utilities.log("Already Syncing...");
        }
    }

    public void destroy() {
        NotificationUtil.cancel(context, 111);
        if (mRealm != null) {
            mRealm.close();
        }
        isSyncing = false;
        ourInstance = null;
        settings.edit().putLong("LastSync", new Date().getTime()).commit();
        if (listener != null) {
            listener.onSyncComplete();
        }
    }

    private void syncDatabase() {
        Thread td = new Thread(() -> {
            try {
                isSyncing = true;
                NotificationUtil.create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...");
                mRealm = dbService.getRealmInstance();

                TransactionSyncManager.syncDb(mRealm, "tablet_users");
                TransactionSyncManager.syncDb(mRealm, "courses");
                TransactionSyncManager.syncDb(mRealm, "exams");
                resourceTransactionSync();
                TransactionSyncManager.syncDb(mRealm, "ratings");
                TransactionSyncManager.syncDb(mRealm, "submissions");
                myLibraryTransactionSync();
                TransactionSyncManager.syncDb(mRealm, "login_activities");
                realm_resourceActivities.onSynced(mRealm, settings);
            } catch (Exception err) {
                err.printStackTrace();
                handleException(err.getMessage());
            } finally {
                destroy();
            }
        });
        td.start();
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
                e.printStackTrace();
            }
        });
    }

    private void syncResource(ApiInterface dbClient) throws IOException {
        int skip = 0;
        int limit = 1000;
        while (true) {
            JsonObject object = new JsonObject();
            object.add("selector", new JsonObject());
            object.addProperty("limit", limit);
            object.addProperty("skip", skip);
            final retrofit2.Call<JsonObject> allDocs = dbClient.findDocs(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/resources/_find", object);
            Response<JsonObject> a = allDocs.execute();
            realm_myLibrary.save(JsonUtils.getJsonArray("docs", a.body()), mRealm);
            if (a.body().size() < limit) {
                break;
            } else {
                skip = skip + limit;
            }
        }
    }


    private void myLibraryTransactionSync() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransaction(realm -> {
            try {
                Utilities.log("URL " + Utilities.getUrl());
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
            if (jsonDoc.getAsJsonArray("resourceIds") != null) {
                for (int i = 0; i < Constants.shelfDataList.size(); i++) {
                    Constants.ShelfData shelfData = Constants.shelfDataList.get(i);
                    JsonArray array = jsonDoc.getAsJsonArray(shelfData.key);
                    memberShelfData(array, shelfData);
                }
            } else {
                Log.e("DB", " BAD Metadata -- Shelf Doc ID " + shelfDoc.getId());
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void memberShelfData(JsonArray array, Constants.ShelfData shelfData) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type);
            check(stringArray, array, shelfData.aClass);
        }
    }

    private void triggerInsert(String categroryId, String categoryDBName) {
        stringArray[0] = shelfDoc.getId();
        stringArray[1] = categroryId;
        stringArray[2] = categoryDBName;
    }


    private void check(String[] stringArray, JsonArray array_categoryIds, Class aClass) {
        for (int x = 0; x < array_categoryIds.size(); x++) {
            if (array_categoryIds.get(x) instanceof JsonNull) {
                continue;
            }
            List db_Categrory = null;
            if (aClass == realm_myLibrary.class || aClass == realm_myCourses.class) {
                db_Categrory =
                        realm_myLibrary.getShelfItem(stringArray[0], mRealm.where(aClass)
                                .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                                .findAll(), aClass);
            }
//            else if (aClass == realm_myCourses.class) {
//                db_Categrory =
//                        realm_myCourses.getMyCourseByUserId(stringArray[0], mRealm.where(aClass)
//                                .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
//                                .findAll());
//            }
            else {
                db_Categrory = mRealm.where(aClass)
                        .contains("userId", stringArray[0])
                        .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                        .findAll();
            }
            checkEmptyAndSave(db_Categrory, x, array_categoryIds);
        }
    }

    private void checkEmptyAndSave(List db_Categrory, int x, JsonArray array_categoryIds) {
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
                realm_myLibrary.insertMyLibrary(stringArray[0], resourceDoc, mRealm);
                break;
            case "meetups":
                realm_meetups.insertMyMeetups(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
            case "courses":
                realm_myCourses.insertMyCourses(stringArray[0], resourceDoc, mRealm);
                break;
            case "teams":
                realm_myTeams.insertMyTeams(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
        }
    }
}
