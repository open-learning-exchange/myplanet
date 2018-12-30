package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_myTeams;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmResults;

public class UploadToShelfService {

    private DatabaseService dbService;
    private SharedPreferences sharedPreferences;
    private Realm mRealm;
    private static UploadToShelfService instance;


    public static UploadToShelfService getInstance() {
        if (instance == null) {
            instance = new UploadToShelfService(MainApplication.context);
        }
        return instance;
    }

    public UploadToShelfService(Context context) {
        sharedPreferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);
    }

    public void uploadToshelf(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<realm_UserModel> users = realm.where(realm_UserModel.class).findAll();
            for (realm_UserModel model : users) {
                try {
                    JsonObject jsonDoc = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + model.getId()).execute().body();
                    JsonObject object = getShelfData(realm, model.getId(), jsonDoc);
                    Utilities.log("JSON " + new Gson().toJson(jsonDoc));
                    JsonObject d = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + model.getId()).execute().body();
                    object.addProperty("_rev", JsonUtils.getString("_rev", d));
                    apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/shelf/" + sharedPreferences.getString("userId", ""), object).execute().body();
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onSuccess("Unable to update documents.");
                }
            }


        }, () -> listener.onSuccess("Sync with server completed successfully"));
    }


    public JsonObject getShelfData(Realm realm, String userId, JsonObject jsonDoc) {
        JsonArray myLibs = realm_myLibrary.getMyLibIds(realm, userId);
        JsonArray myCourses = realm_myCourses.getMyCourseIds(realm, userId);
        JsonArray myTeams = realm_myTeams.getMyTeamIds(realm, userId);
        JsonArray myMeetups = realm_meetups.getMyMeetUpIds(realm, userId);


        JsonObject object = new JsonObject();
        object.addProperty("_id", sharedPreferences.getString("userId", ""));
        object.add("meetupIds", mergeJsonArray(myMeetups, JsonUtils.getJsonArray("meetupIds", jsonDoc)));
        object.add("resourceIds", mergeJsonArray(myLibs, JsonUtils.getJsonArray("resourceIds", jsonDoc)));
        object.add("courseIds", mergeJsonArray(myCourses, JsonUtils.getJsonArray("courseIds", jsonDoc)));
        object.add("myTeamIds", mergeJsonArray(myTeams, JsonUtils.getJsonArray("myTeamIds", jsonDoc)));
        return object;
    }

    public JsonArray mergeJsonArray(JsonArray array1, JsonArray array2) {
        JsonArray array = new JsonArray();
        array.addAll(array1);
        for (JsonElement e : array2) {
           if (!array.contains(e)){
               array.add(e);
           }
        }
        return array;
    }
}
