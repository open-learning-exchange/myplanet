package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.JsonUtils;
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


    public static void syncKeyIv(Realm mRealm, SharedPreferences settings, SyncListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        listener.onSyncStarted();
        RealmUserModel model = new UserProfileDbHandler(MainApplication.context).getUserModel();
        String userName = settings.getString("loginUserName", "");
        String password = settings.getString("loginUserPassword", "");
        String table = "userdb-" + Utilities.toHex(model.getPlanetCode()) + "-" + Utilities.toHex(model.getName());
        String header = "Basic " + Base64.encodeToString((userName + ":" + password).getBytes(), Base64.NO_WRAP);
        String id = model.getId();
        mRealm.executeTransactionAsync(realm -> {
            Response response;
            try {
                RealmUserModel userModel = realm.where(RealmUserModel.class).equalTo("id", id).findFirst();
                response = apiInterface.getDocuments(header, Utilities.getUrl() + "/" + table + "/_all_docs").execute();
                DocumentResponse ob = (DocumentResponse) response.body();
                if (ob!=null && ob.getRows().size() > 0){
                    Rows r = ob.getRows().get(0);
                    JsonObject jsonDoc = apiInterface.getJsonObject(header, Utilities.getUrl() + "/" + table + "/" + r.getId()).execute().body();
                    userModel.setKey(JsonUtils.getString("key", jsonDoc));
                    userModel.setIv(JsonUtils.getString("iv", jsonDoc));
                }
            } catch (IOException e) {
            }
        }, listener::onSyncComplete, error -> listener.onSyncFailed(error.getMessage()));
    }

    public static void syncDb(final Realm mRealm, final String table) {
        Utilities.log("Sync table  " + table);
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
                e.printStackTrace();
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
        } catch (Exception e) {}
    }


}
