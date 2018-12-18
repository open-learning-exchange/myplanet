package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.ole.planet.myplanet.Data.realm_courseProgress;
import org.ole.planet.myplanet.Data.realm_feedback;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_myTeams;
import org.ole.planet.myplanet.Data.realm_offlineActivities;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.Data.realm_resourceActivities;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import okhttp3.ResponseBody;

public class UploadManager {
    private DatabaseService dbService;
    private Context context;
    private SharedPreferences sharedPreferences;
    private Realm mRealm;
    private static UploadManager instance;

    public static UploadManager getInstance() {
        if (instance == null) {
            instance = new UploadManager(MainApplication.context);
        }
        return instance;
    }


    public UploadManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        dbService = new DatabaseService(context);

    }

    public void uploadExamResult(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<realm_submissions> submissions = realm.where(realm_submissions.class).equalTo("status", "graded").equalTo("uploaded", false).findAll();
            for (realm_submissions sub : submissions) {
                try {
                    realm_submissions.continueResultUpload(sub, apiInterface, realm);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, () -> listener.onSuccess("Result sync completed successfully"));
        uploadCourseProgress();
    }

    public void uploadCourseProgress() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<realm_courseProgress> data = realm.where(realm_courseProgress.class)
                    .isNull("_id").findAll();
            for (realm_courseProgress sub : data) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/courses_progress", realm_courseProgress.serializeProgress(sub)).execute().body();
                    if (object != null) {
                        sub.set_id(JsonUtils.getString("_id", object));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public void uploadFeedback(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<realm_feedback> feedbacks = realm.where(realm_feedback.class).equalTo("uploaded", false).findAll();
            for (realm_feedback feedback : feedbacks) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/feedback", realm_feedback.serializeFeedback(feedback)).execute().body();
                    if (object!=null) {
                        feedback.setUploaded(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }, () -> listener.onSuccess("Feedback sync completed successfully"));
    }

    public void uploadToshelf(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);

        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            JsonObject object = getShelfData(realm);
            try {
                JsonObject d = apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/shelf/" + sharedPreferences.getString("userId", "")).execute().body();
                object.addProperty("_rev", JsonUtils.getString("_rev", d));
                apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/shelf/" + sharedPreferences.getString("userId", ""), object).execute().body();
            } catch (Exception e) {
                listener.onSuccess("Unable to update documents.");
            }

        }, () -> listener.onSuccess("Sync with server completed successfully"));
    }

    public void uploadUserActivities(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<realm_offlineActivities> activities = realm.where(realm_offlineActivities.class)
                    .isNull("_rev").equalTo("type", "login").findAll();
            for (realm_offlineActivities act : activities) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/login_activities", realm_offlineActivities.serializeLoginActivities(act)).execute().body();
                    act.changeRev(object);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }, () -> listener.onSuccess("Sync with server completed successfully"));
    }

    public void uploadRating(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<realm_rating> activities = realm.where(realm_rating.class).equalTo("isUpdated", true).findAll();
            for (realm_rating act : activities) {
                try {
                    JsonObject object;
                    if (TextUtils.isEmpty(act.get_id())) {
                        object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings", realm_rating.serializeRating(act)).execute().body();
                    } else {
                        object = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings/" + act.get_id(), realm_rating.serializeRating(act)).execute().body();
                    }
                    if (object != null) {
                        act.set_id(JsonUtils.getString("_id", object));
                        act.set_rev(JsonUtils.getString("_rev", object));
                        act.setUpdated(false);
                    }

                } catch (Exception e) {
                }
            }
        });
    }

    public void uploadResourceActivities(String type) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);

        String db = type.equals("sync") ? "admin_activities" : "resource_activities";
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<realm_resourceActivities> activities;
            if (type.equals("sync")) {
                activities = realm.where(realm_resourceActivities.class).isNull("_rev").equalTo("type", "sync").findAll();
            } else {
                activities = realm.where(realm_resourceActivities.class).isNull("_rev").notEqualTo("type", "sync").findAll();
            }
            for (realm_resourceActivities act : activities) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/" + db, realm_resourceActivities.serializeResourceActivities(act)).execute().body();
                    if (object != null) {
                        act.set_rev(JsonUtils.getString("_rev", object));
                        act.set_id(JsonUtils.getString("_id", object));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    public JsonObject getShelfData(Realm realm) {
        JsonArray myLibs = realm_myLibrary.getMyLibIds(realm, sharedPreferences);
        JsonArray myCourses = realm_myCourses.getMyCourseIds(realm, sharedPreferences);
        JsonArray myTeams = realm_myTeams.getMyTeamIds(realm, sharedPreferences);
        JsonArray myMeetups = realm_meetups.getMyMeetUpIds(realm, sharedPreferences);
        JsonObject object = new JsonObject();
        object.addProperty("_id", sharedPreferences.getString("userId", ""));
        object.add("meetupIds", myMeetups);
        object.add("resourceIds", myLibs);
        object.add("courseIds", myCourses);
        object.add("myTeamIds", myTeams);
        return object;
    }
}
