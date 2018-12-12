package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_offlineActivities;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;

import java.util.List;

import io.realm.Realm;

public class TransactionSyncManager {

    public static void syncDb(final Realm mRealm, final CouchDbProperties properties, final String type) {
        mRealm.executeTransaction(realm -> {
            final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
            final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
            for (int i = 0; i < allDocs.size(); i++) {
                Document doc = allDocs.get(i);
                try {
                    processDoc(dbClient, doc, mRealm, type);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void processDoc(CouchDbClientAndroid dbClient, Document doc, Realm mRealm, String type) throws Exception {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (type.equals("course")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_myCourses.insertMyCourses(jsonDoc, mRealm);
        } else if (type.equals("exams")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_stepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
        } else if (type.equals("users")) {
            processUserDoc(doc, dbClient, mRealm, settings);
        } else if (type.equals("login")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_offlineActivities.insertOfflineActivities(mRealm, jsonDoc);
        }
        checkDoc(dbClient, doc, mRealm, type);
    }

    private static void checkDoc(CouchDbClientAndroid dbClient, Document doc, Realm mRealm, String type) {
        if (type.equals("submissions")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_submissions.insertSubmission(mRealm, jsonDoc);
        } else if (type.equals("rating")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_rating.insertRatings(mRealm, jsonDoc);
        }
    }

    private static void processUserDoc(Document doc, CouchDbClientAndroid dbClient, Realm mRealm, SharedPreferences settings) {
        if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
            JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
            realm_UserModel.populateUsersTable(jsonDoc, mRealm, settings);
        }
    }

}
