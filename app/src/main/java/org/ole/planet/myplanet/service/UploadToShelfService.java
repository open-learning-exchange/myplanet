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
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import retrofit2.Response;

public class UploadToShelfService {

    private static UploadToShelfService instance;
    private DatabaseService dbService;
    private SharedPreferences sharedPreferences;
    private Realm mRealm;


    public UploadToShelfService(Context context) {
        sharedPreferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
    }

    public static UploadToShelfService getInstance() {
        if (instance == null) {
            instance = new UploadToShelfService(MainApplication.context);
        }
        return instance;
    }

    public void uploadUserData(SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmUserModel> userModels = realm.where(RealmUserModel.class).isEmpty("_id").or().equalTo("updated", true).findAll();
            Utilities.log("USER LIST SIZE + " + userModels.size());
            for (RealmUserModel model : userModels) {
                try {
                    Response<JsonObject> res = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/_users/org.couchdb.user:" + model.getName()).execute();
                    if (res.body() == null) {
                        JsonObject obj = model.serialize();
                        res = apiInterface.putDoc(null, "application/json", Utilities.getUrl() + "/_users/org.couchdb.user:" + model.getName(), obj).execute();
                        if (res.body() != null) {
                            String id = res.body().get("id").getAsString();
                            String rev = res.body().get("rev").getAsString();
                            res = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/_users/" + id).execute();
                            if (res.body() != null) {
                                model.set_id(id);
                                model.set_rev(rev);
                                model.setPassword_scheme(JsonUtils.getString("password_scheme", res.body()));
                                model.setDerived_key(JsonUtils.getString("derived_key", res.body()));
                                model.setSalt(JsonUtils.getString("salt", res.body()));
                                model.setIterations(JsonUtils.getString("iterations", res.body()));
                                if (saveKeyIv(apiInterface, model, obj))
                                    updateHealthData(realm, model);
                            }
                        }
                    } else if (model.isUpdated()) {
                        Utilities.log("UPDATED MODEL " + model.serialize());
                        JsonObject obj = model.serialize();
                        res = apiInterface.putDoc(null, "application/json", Utilities.getUrl() + "/_users/org.couchdb.user:" + model.getName(), obj).execute();

                        if (res.body() != null) {
                            Utilities.log(new Gson().toJson(res.body()));
                            String rev = res.body().get("rev").getAsString();
                            model.set_rev(rev);
                            model.setUpdated(false);
                        }else{
                            Utilities.log(res.errorBody().string());
                        }
                    } else {
                        Utilities.toast(MainApplication.context, "User " + model.getName() + " already exist");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, () -> {
            uploadToshelf(listener);
        }, (err) -> {
            uploadToshelf(listener);
        });

    }

    private void updateHealthData(Realm realm, RealmUserModel model) {
        List<RealmMyHealthPojo> list = realm.where(RealmMyHealthPojo.class).equalTo("_id", model.getId()).findAll();
        for (RealmMyHealthPojo p : list) {
            p.setUserId(model.get_id());
//            try {
//                p.setData(AndroidDecrypter.encrypt(p.getData(), model.getKey(), model.getId()));
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        }
    }


    private static void changeUserSecurity(RealmUserModel model, JsonObject obj) {
        String table = "userdb-" + Utilities.toHex(model.getPlanetCode()) + "-" + Utilities.toHex(model.getName());
        String header = "Basic " + Base64.encodeToString((obj.get("name").getAsString() + ":" + obj.get("password").getAsString()).getBytes(), Base64.NO_WRAP);
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        Response<JsonObject> response;
        try {
            response = apiInterface.getJsonObject(header, Utilities.getUrl() + "/" + table + "/_security").execute();
            if (response.body() != null) {
                JsonObject jsonObject = response.body();
                JsonObject members = jsonObject.getAsJsonObject("members");
                JsonArray rolesArray;
                if (members.has("roles")) {
                    rolesArray = members.getAsJsonArray("roles");
                } else {
                    rolesArray = new JsonArray();
                }
                rolesArray.add("health");
                members.add("roles", rolesArray);
                jsonObject.add("members", members);
                response = apiInterface.putDoc(header, "application/json", Utilities.getUrl() + "/" + table + "/_security", jsonObject).execute();
                if (response.body() != null) {
                    Utilities.log("Update security  " + new Gson().toJson(response.body()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean saveKeyIv(ApiInterface apiInterface, RealmUserModel model, JsonObject obj) throws IOException {
        String table = "userdb-" + Utilities.toHex(model.getPlanetCode()) + "-" + Utilities.toHex(model.getName());
        String header = "Basic " + Base64.encodeToString((obj.get("name").getAsString() + ":" + obj.get("password").getAsString()).getBytes(), Base64.NO_WRAP);
        JsonObject ob = new JsonObject();
        String keyString = AndroidDecrypter.generateKey();
        String iv = AndroidDecrypter.generateIv();
        if (!TextUtils.isEmpty(model.getIv())) iv = model.getIv();
        if (!TextUtils.isEmpty(model.getKey())) keyString = model.getKey();
        ob.addProperty("key", keyString);
        ob.addProperty("iv", iv);
        ob.addProperty("createdOn", new Date().getTime());
        boolean success = false;
        while (!success) {
            Response response = apiInterface.postDoc(header, "application/json", Utilities.getUrl() + "/" + table, ob).execute();
            if (response.body() != null) {
                model.setKey(keyString);
                model.setIv(iv);
                success = true;
            } else {
                success = false;
            }
        }
        changeUserSecurity(model, obj);
        return true;
    }


    public void uploadHealth() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmMyHealthPojo> myHealths = realm.where(RealmMyHealthPojo.class).equalTo("isUpdated", true).notEqualTo("userId", "").findAll();
            for (RealmMyHealthPojo pojo : myHealths) {
                try {
                    Response<JsonObject> res = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/health", RealmMyHealthPojo.serialize(pojo)).execute();
                    if (res.body() != null && res.body().has("id")) {
                        pojo.set_rev(res.body().get("rev").getAsString());
                        pojo.setIsUpdated(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public void uploadToshelf(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmUserModel> users = realm.where(RealmUserModel.class).isNotEmpty("_id").findAll();
            for (RealmUserModel model : users) {
                try {
                    if (model.getId().startsWith("guest"))
                        continue;
                    JsonObject jsonDoc = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + model.get_id()).execute().body();
                    JsonObject object = getShelfData(realm, model.getId(), jsonDoc);
                    Utilities.log("JSON " + new Gson().toJson(jsonDoc));
                    JsonObject d = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + model.getId()).execute().body();
                    object.addProperty("_rev", JsonUtils.getString("_rev", d));
                    apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/shelf/" + sharedPreferences.getString("userId", ""), object).execute().body();
                } catch (Exception e) {
                    e.printStackTrace();

                }
            }
        }, () -> listener.onSuccess("Sync with server completed successfully"), (err) -> {
            listener.onSuccess("Unable to update documents.");
        });
    }


    public JsonObject getShelfData(Realm realm, String userId, JsonObject jsonDoc) {
        JsonArray myLibs = RealmMyLibrary.getMyLibIds(realm, userId);
        JsonArray myCourses = RealmMyCourse.getMyCourseIds(realm, userId);
//        JsonArray myTeams = RealmMyTeam.getMyTeamIds(realm, userId);
        JsonArray myMeetups = RealmMeetup.getMyMeetUpIds(realm, userId);

        List<String> removedResources = Arrays.asList(RealmRemovedLog.removedIds(realm, "resources", userId));
        List<String> removedCourses = Arrays.asList(RealmRemovedLog.removedIds(realm, "courses", userId));

        JsonArray mergedResourceIds = mergeJsonArray(myLibs, JsonUtils.getJsonArray("resourceIds", jsonDoc), removedResources);
        JsonArray mergedCoueseIds = mergeJsonArray(myCourses, JsonUtils.getJsonArray("courseIds", jsonDoc), removedCourses);

        JsonObject object = new JsonObject();


        object.addProperty("_id", sharedPreferences.getString("userId", ""));
        object.add("meetupIds", mergeJsonArray(myMeetups, JsonUtils.getJsonArray("meetupIds", jsonDoc), removedResources));
        object.add("resourceIds", mergedResourceIds);
        object.add("courseIds", mergedCoueseIds);
//        object.add("myTeamIds", mergeJsonArray(myTeams, JsonUtils.getJsonArray("myTeamIds", jsonDoc), removedResources));
        return object;
    }


    public JsonArray mergeJsonArray(JsonArray array1, JsonArray array2, List<String> removedIds) {
        JsonArray array = new JsonArray();
        array.addAll(array1);
        for (JsonElement e : array2) {
            if (!array.contains(e) && !removedIds.contains(e.getAsString())) {
                array.add(e);
            }
        }
        return array;
    }
}
