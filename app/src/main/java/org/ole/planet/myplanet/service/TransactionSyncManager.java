package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.DocumentResponse;
import org.ole.planet.myplanet.Data.Rows;
import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_offlineActivities;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;

import io.realm.Realm;

public class TransactionSyncManager {

    public static void syncDb(final Realm mRealm, final String table) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);

        mRealm.executeTransactionAsync(realm -> {
            try {
                DocumentResponse res = apiInterface.getDocuments(Utilities.getHeader(), Utilities.getUrl() + "/" + table + "/_all_docs").execute().body();
                for (int i = 0; i < res.getRows().size(); i++) {
                    Rows doc = res.getRows().get(i);
                    try {
                        processDoc(apiInterface, doc, realm, table);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void processDoc(ApiInterface dbClient, Rows doc, Realm mRealm, String type) throws Exception {
        if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
            JsonObject jsonDoc = dbClient.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/" + type + "/" + doc.getId()).execute().body();
            if (type.equals("courses")) {
                realm_myCourses.insertMyCourses(jsonDoc, mRealm);
            } else if (type.equals("exams")) {
                realm_stepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
            }
            checkDoc(jsonDoc, mRealm, type);
        }
    }

    private static void checkDoc(JsonObject jsonDoc, Realm mRealm, String type) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (type.equals("submissions")) {
            realm_submissions.insertSubmission(mRealm, jsonDoc);
        } else if (type.equals("ratings")) {
            realm_rating.insertRatings(mRealm, jsonDoc);
        } else if (type.equals("tablet_users")) {
            realm_UserModel.populateUsersTable(jsonDoc, mRealm, settings);
        } else if (type.equals("login_activities")) {
            realm_offlineActivities.insertOfflineActivities(mRealm, jsonDoc);
        }
    }

}
