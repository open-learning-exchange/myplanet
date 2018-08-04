package org.ole.planet.takeout.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myTeams;
import org.ole.planet.takeout.Data.realm_resources;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.SyncListener;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.NotificationUtil;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class SyncManager {
    private static SyncManager ourInstance;
    private SharedPreferences settings;
    private Realm mRealm;
    private CouchDbProperties properties;
    private Context context;
    private boolean isSyncing = false;
    static final String PREFS_NAME = "OLE_PLANET";
    private String[] stringArray = new String[4];
    private Document shelfDoc;
    private SyncListener listener;
    private DatabaseService dbService;

    public static SyncManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new SyncManager(MainApplication.context);
        }
        return ourInstance;
    }


    private SyncManager(Context context) {
        this.context = context;
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
    }


    public void start(SyncListener listener) {
        this.listener = listener;
        if (!isSyncing) {
            syncDatabase();
        } else {
            Utilities.log("Already Syncing...");
        }
    }

    public void destroy() {
        isSyncing = false;
        ourInstance = null;
        settings.edit().putLong("LastSync", new Date().getTime()).commit();
        if (listener != null) {
            listener.onSyncComplete();
        }
    }

    private void syncDatabase() {
        if (listener != null) {
            listener.onSyncStarted();
        }
        Thread td = new Thread(new Runnable() {
            public void run() {
                try {
                    isSyncing = true;
                    NotificationUtil.create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...");
                    mRealm = dbService.getRealmInstance();
                    properties = dbService.getClouchDbProperties("_users", settings);
                    TransactionSyncManager.syncDb(mRealm, properties, "users");
                    myLibraryTransactionSync();
                    TransactionSyncManager.syncDb(mRealm, dbService.getClouchDbProperties("courses", settings), "course");
                    resourceTransactionSync();
                } finally {
                    NotificationUtil.cancel(context, 111);
                    isSyncing = false;
                    if (mRealm != null) {
                        mRealm.close();
                    }
                    destroy();
                }
            }
        });
        td.start();
    }


    public void resourceTransactionSync() {
        final CouchDbProperties properties = dbService.getClouchDbProperties("resources", settings);
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++) {
                    Document doc = allDocs.get(i);
                    Utilities.log("Document " + doc);
                    processResourceDoc(dbClient, doc);
                }
            }
        });
    }

    private void processResourceDoc(CouchDbClientAndroid dbClient, Document doc) {
        try {

            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_resources.insertResources(jsonDoc, mRealm);
            Log.e("Realm", " STRING " + jsonDoc.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void myLibraryTransactionSync() {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
                List<Document> allShelfDocs = dbShelfClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allShelfDocs.size(); i++) {
                    shelfDoc = allShelfDocs.get(i);
                    populateShelfItems(settings, realm);
                }
            }
        });
    }


    private void populateShelfItems(SharedPreferences settings, Realm mRealm) {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
        try {
            this.mRealm = mRealm;
            JsonObject jsonDoc = dbShelfClient.find(JsonObject.class, shelfDoc.getId());
            Utilities.log("Json Doc " + jsonDoc.toString());
            if (jsonDoc.getAsJsonArray("resourceIds") != null) {
                JsonArray array_resourceIds = jsonDoc.getAsJsonArray("resourceIds");
                JsonArray array_meetupIds = jsonDoc.getAsJsonArray("meetupIds");
                JsonArray array_courseIds = jsonDoc.getAsJsonArray("courseIds");
                JsonArray array_myTeamIds = jsonDoc.getAsJsonArray("myTeamIds");
                memberShelfData(array_resourceIds, array_meetupIds, array_courseIds, array_myTeamIds);
            } else {
                Log.e("DB", " BAD Metadata -- Shelf Doc ID " + shelfDoc.getId());
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void memberShelfData(JsonArray array_resourceIds, JsonArray array_meetupIds, JsonArray array_courseIds, JsonArray array_myTeamIds) {
        //    setVariables(settings, mRealm, properties);
        if (array_resourceIds.size() > 0) {
            RealmResults<realm_myLibrary> category = null;
            triggerInsert("resourceId", "resources");
            check(stringArray, array_resourceIds, realm_myLibrary.class, category);
        }
        if (array_meetupIds.size() > 0) {
            triggerInsert("meetupId", "meetups");
            RealmResults<realm_meetups> category = null;
            check(stringArray, array_meetupIds, realm_meetups.class, category);
        }
        if (0 < array_courseIds.size()) {
            RealmResults<realm_myCourses> category = null;
            triggerInsert("courseId", "courses");
            check(stringArray, array_courseIds, realm_myCourses.class, category);
        }
        if (1 <= array_myTeamIds.size()) {
            RealmResults<realm_myTeams> category = null;
            triggerInsert("teamId", "teams");
            check(stringArray, array_myTeamIds, realm_myTeams.class, category);
        }
//        if (array_myTeamIds.size() > 0) {
//            checkMyTeams(shelfDoc.getId(), array_myTeamIds);
//        }
    }

    private void triggerInsert(String categroryId, String categoryDBName) {
        stringArray[0] = shelfDoc.getId();
        stringArray[1] = categroryId;
        stringArray[2] = categoryDBName;
    }


    private void check(String[] stringArray, JsonArray array_categoryIds, Class aClass, RealmResults<?> db_Categrory) {
        for (int x = 0; x < array_categoryIds.size(); x++) {
            db_Categrory = mRealm.where(aClass)
                    .equalTo("userId", stringArray[0])
                    .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                    .findAll();
            if (db_Categrory.isEmpty()) {
                setRealmProperties(stringArray[2]);
                CouchDbClientAndroid generaldb = new CouchDbClientAndroid(properties);
                JsonObject resourceDoc = generaldb.find(JsonObject.class, array_categoryIds.get(x).getAsString());
                triggerInsert(stringArray, array_categoryIds, x, resourceDoc);
            } else {
                Log.e("DATA", " Data already saved for -- " + stringArray[0] + " " + array_categoryIds.get(x).getAsString());
            }
        }
    }

    private void triggerInsert(String[] stringArray, JsonArray array_categoryIds, int x, JsonObject resourceDoc) {
        switch (stringArray[2]) {
            case "resources":
                realm_myLibrary.insertMyLibrary(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm, settings);
                break;
            case "meetups":
                realm_meetups.insertMyMeetups(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
            case "courses":
                realm_myCourses.insertMyCourses(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
            case "teams":
                realm_myTeams.insertMyTeams(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc, mRealm);
                break;
        }
    }
//
//    private void checkMyTeams(String userId, JsonArray array_myTeamIds) {
//        for (int tms = 0; tms < array_myTeamIds.size(); tms++) {
//        }
//    }


    private void setRealmProperties(String dbName) {
        properties.setDbName(dbName);
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
    }


//    public void insertMyTeams(realm_meetups myMyTeamsDB, String userId, String myTeamsID, JsonObject myTeamsDoc) {
//
//    }

}
