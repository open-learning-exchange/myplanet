package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
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


    public static void syncAllHealthData(Realm mRealm, SharedPreferences settings, SyncListener listener) {
        listener.onSyncStarted();
        String userName = settings.getString("loginUserName", "");
        String password = settings.getString("loginUserPassword", "");
        String header = "Basic " + Base64.encodeToString((userName + ":" + password).getBytes(), Base64.NO_WRAP);
        mRealm.executeTransactionAsync(realm -> {
            Utilities.log("Sync");
            RealmResults<RealmUserModel> users = realm.where(RealmUserModel.class).isNotEmpty("_id").findAll();
            for (RealmUserModel userModel : users) {
                Utilities.log("Sync " + userModel.getName());
                syncHealthData(userModel, header);
            }

        }, listener::onSyncComplete, error -> listener.onSyncFailed(error.getMessage()));
    }

    private static void syncHealthData(RealmUserModel userModel, String header) {
        String table = "userdb-" + Utilities.toHex(userModel.getPlanetCode()) + "-" + Utilities.toHex(userModel.getName());
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        Response response;
        try {
            response = apiInterface.getDocuments(header, Utilities.getUrl() + "/" + table + "/_all_docs").execute();
            DocumentResponse ob = (DocumentResponse) response.body();
            if (ob != null && ob.getRows().size() > 0) {
                Rows r = ob.getRows().get(0);
                JsonObject jsonDoc = apiInterface.getJsonObject(header, Utilities.getUrl() + "/" + table + "/" + r.getId()).execute().body();
                userModel.setKey(JsonUtils.getString("key", jsonDoc));
                userModel.setIv(JsonUtils.getString("iv", jsonDoc));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void syncKeyIv(Realm mRealm, SharedPreferences settings, SyncListener listener) {
        listener.onSyncStarted();
        RealmUserModel model = new UserProfileDbHandler(MainApplication.context).getUserModel();
        String userName = settings.getString("loginUserName", "");
        String password = settings.getString("loginUserPassword", "");
        String table = "userdb-" + Utilities.toHex(model.getPlanetCode()) + "-" + Utilities.toHex(model.getName());
        String header = "Basic " + Base64.encodeToString((userName + ":" + password).getBytes(), Base64.NO_WRAP);
        String id = model.getId();
        mRealm.executeTransactionAsync(realm -> {
            RealmUserModel userModel = realm.where(RealmUserModel.class).equalTo("id", id).findFirst();
            syncHealthData(userModel, header);
        }, listener::onSyncComplete, error -> listener.onSyncFailed(error.getMessage()));
    }


    public static void syncDb(Realm realm, String table) {
        realm.executeTransactionAsync(mRealm -> {
            ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
            final retrofit2.Call<JsonObject> allDocs = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/" + table + "/_all_docs?include_doc=false");
            try {
                Response<JsonObject> all = allDocs.execute();
                JsonArray rows = JsonUtils.getJsonArray("rows", all.body());
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    JsonObject object = rows.get(i).getAsJsonObject();
                    if (!TextUtils.isEmpty(JsonUtils.getString("id", object)))
                        keys.add(JsonUtils.getString("key", object));
                    if (i == rows.size() - 1 || keys.size() == 1000) {
                        JsonObject obj = new JsonObject();
                        obj.add("keys", new Gson().fromJson(new Gson().toJson(keys), JsonArray.class));
                        final Response<JsonObject> response = apiInterface.findDocs(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/" + table + "/_all_docs?include_docs=true", obj).execute();
                        if (response.body() != null) {
                            JsonArray arr = JsonUtils.getJsonArray("rows", response.body());
                            insertDocs(arr, mRealm, table);
                        }
                        keys.clear();
                    }
                }
            } catch (IOException e) {
            }
        });
    }

    private static void insertDocs(JsonArray arr, Realm mRealm, String table) {
        for (JsonElement j : arr) {
            JsonObject jsonDoc = j.getAsJsonObject();
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc);
            String id = JsonUtils.getString("_id", jsonDoc);
            if (!id.startsWith("_design")) {
                continueInsert(mRealm, table, jsonDoc);
            }
        }
    }

    private static void continueInsert(Realm mRealm, String table, JsonObject jsonDoc) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (table.equals("exams")) {
            RealmStepExam.insertCourseStepsExams("", "", jsonDoc, mRealm);
        } else if (table.equals("tablet_users")) {
            RealmUserModel.populateUsersTable(jsonDoc, mRealm, settings);
        } else {
            callMethod(mRealm, jsonDoc, table);
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
        }
    }


}
