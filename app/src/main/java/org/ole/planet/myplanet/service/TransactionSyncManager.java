package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.lang.reflect.Method;

import io.realm.Realm;
import retrofit2.Response;

public class TransactionSyncManager {
    public static boolean authenticate() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        try {
            Response response = apiInterface.getDocuments(Utilities.getHeader(), Utilities.getUrl() + "/tablet_users/_all_docs").execute();
            return response.code() == 200;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


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
                    }
                }
            } catch (IOException e) {
            }
        });
    }

    private static void processDoc(ApiInterface dbClient, Rows doc, Realm mRealm, String type) throws Exception {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
            JsonObject jsonDoc = dbClient.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/" + type + "/" + doc.getId()).execute().body();
            if (type.equals("exams")) {
                RealmStepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
            } else if (type.equals("tablet_users")) {
                RealmUserModel.populateUsersTable(jsonDoc, mRealm, settings);
            } else {
                callMethod(mRealm, jsonDoc, type);
            }
        }
    }

    private static void callMethod(Realm mRealm, JsonObject jsonDoc, String type) {
        try {
            Method[] methods = Constants.classList.get(type).getMethods();
            for (Method m : methods) {
                if ("insert".equals(m.getName())) {
                    m.invoke(null, mRealm, jsonDoc);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
