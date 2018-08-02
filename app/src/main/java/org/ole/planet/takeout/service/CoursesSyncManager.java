package org.ole.planet.takeout.service;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class CoursesSyncManager {

    Realm mRealm;
    DatabaseService dbService;
    SharedPreferences settings;

    public CoursesSyncManager(Realm mRealm, DatabaseService dbService, SharedPreferences settings) {
        this.mRealm = mRealm;
        this.dbService = dbService;
        this.settings = settings;
    }

    public void coursesTransactionSync() {
        final CouchDbProperties properties = dbService.getClouchDbProperties("courses", settings);
        DatabaseService.syncDB(mRealm, properties, "course");
//        mRealm.executeTransaction(new Realm.Transaction() {
//            @Override
//            public void execute(Realm realm) {
//                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
//                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
//                for (int i = 0; i < allDocs.size(); i++) {
//                    Document doc = allDocs.get(i);
//                    processCourseDoc(dbClient, doc);
//                }
//            }
//        });
    }


}
