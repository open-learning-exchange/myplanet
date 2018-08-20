package org.ole.planet.takeout.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myTeams;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.SyncListener;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.Constants;
import org.ole.planet.takeout.utilities.NotificationUtil;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class SyncManager {
    static final String PREFS_NAME = "OLE_PLANET";
    private static SyncManager ourInstance;
    private SharedPreferences settings;
    private Realm mRealm;
    private CouchDbProperties properties;
    private Context context;
    private boolean isSyncing = false;
    private String[] stringArray = new String[4];
    private Document shelfDoc;
    private SyncListener listener;
    private DatabaseService dbService;

    private SyncManager(Context context) {
        this.context = context;
        settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
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
                    TransactionSyncManager.syncDb(mRealm, dbService.getClouchDbProperties("exams", settings), "exams");
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
            realm_myLibrary.insertResources(jsonDoc, mRealm);
            Log.e("Realm", " STRING " + jsonDoc.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public void examTransactionSync() {
//        final CouchDbProperties properties = dbService.getClouchDbProperties("exams", settings);
//        mRealm.executeTransaction(new Realm.Transaction() {
//            @Override
//            public void execute(Realm realm) {
//                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
//                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
//                for (int i = 0; i < allDocs.size(); i++) {
//                    Document doc = allDocs.get(i);
//                    Utilities.log("Document " + doc);
//                    processExamDoc(dbClient, doc);
//                }
//            }
//        });
//    }
//
//    private void processExamDoc(CouchDbClientAndroid dbClient, Document doc) {
//        try {
//
//            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
//            realm_stepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
//            Log.e("Realm", " STRING " + jsonDoc.toString());
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

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
            RealmResults db_Categrory = mRealm.where(aClass)
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
                Utilities.log("Resource  " + stringArray[0]);
                realm_myLibrary.insertMyLibrary(stringArray[0], resourceDoc, mRealm);
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

    private void setRealmProperties(String dbName) {
        properties.setDbName(dbName);
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
    }
}
