package org.ole.planet.takeout.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;

import java.util.List;

import io.realm.Realm;

public class TransactionSyncManager {
    public static void syncDb(final Realm mRealm, final CouchDbProperties properties, final String type) {
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++) {
                    Document doc = allDocs.get(i);
                    processDoc(dbClient, doc, mRealm, type);
                }

            }
        });
    }

    private static void processDoc(CouchDbClientAndroid dbClient, Document doc, Realm mRealm, String type) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        try {
            if (type.equals("course")) {
                JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                realm_courses.insertMyCourses(jsonDoc, mRealm);
            }
            if (type.equals("exams")) {
                JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                realm_stepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
            } else if (type.equals("users")) {
                if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
                    JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                    realm_UserModel.populateUsersTable(jsonDoc, mRealm, settings);
                    Log.e("Realm", " STRING " + jsonDoc.get("_id"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
