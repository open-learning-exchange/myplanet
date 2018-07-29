package org.ole.planet.takeout.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class DatabaseService {

    private Context context;

    public DatabaseService(Context context) {
        this.context = context;
    }

    public Realm getRealmInstance() {
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();

        Realm.setDefaultConfiguration(config);
        return Realm.getInstance(config);
    }

    public CouchDbProperties getClouchDbProperties(String dbName, SharedPreferences settings) {
        return new CouchDbProperties()
                .setDbName(dbName)
                .setCreateDbIfNotExist(false)
                .setProtocol(settings.getString("url_Scheme", "http"))
                .setHost(settings.getString("url_Host", "192.168.2.1"))
                .setPort(settings.getInt("url_Port", 3000))
                .setUsername(settings.getString("url_user", ""))
                .setPassword(settings.getString("url_pwd", ""))
                .setMaxConnections(100)
                .setConnectionTimeout(0);
    }

    public static void syncDB(final Realm mRealm, final CouchDbProperties properties, final String type) {
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++) {
                    Document doc = allDocs.get(i);
                    if (type.equals("user"))
                        processUserDoc(dbClient, doc, mRealm);
                    else if(type.equals("course")){
                        processCourseDoc(dbClient, doc, mRealm);
                    }
                }
            }
        });
    }



    private static void processCourseDoc(CouchDbClientAndroid dbClient, Document doc, Realm mRealm) {
        try {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_courses.insertMyCourses(jsonDoc, mRealm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processUserDoc(CouchDbClientAndroid dbClient, Document doc, Realm mRealm) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        try {
            if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
                JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                realm_UserModel.populateUsersTable(jsonDoc, mRealm, settings);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
