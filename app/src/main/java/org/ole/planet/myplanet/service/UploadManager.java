package org.ole.planet.myplanet.service;

import android.content.Context;
import android.text.TextUtils;

import com.github.kittinunf.fuel.android.core.Json;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmApkLog;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmOfflineActivity;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmResourceActivity;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class UploadManager {
    private DatabaseService dbService;
    private Realm mRealm;
    private static UploadManager instance;

    public static UploadManager getInstance() {
        if (instance == null) {
            instance = new UploadManager(MainApplication.context);
        }
        return instance;
    }

    public UploadManager(Context context) {
        dbService = new DatabaseService(context);
    }

    public void uploadExamResult(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<RealmSubmission> submissions = realm.where(RealmSubmission.class).equalTo("status", "graded").equalTo("uploaded", false).findAll();
            for (RealmSubmission sub : submissions) {
                try {
                    RealmSubmission.continueResultUpload(sub, apiInterface, realm);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, () -> listener.onSuccess("Result sync completed successfully"));
        uploadCourseProgress();
    }

    public void uploadAchievement() {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<RealmAchievement> list = realm.where(RealmAchievement.class).findAll();
            for (RealmAchievement sub : list) {
                try {
                    JsonObject ob = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/achievements/" + sub.get_id(), RealmAchievement.serialize(sub)).execute().body();
                    if (ob == null){
                        ResponseBody re = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/achievements", RealmAchievement.serialize(sub)).execute().errorBody();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void uploadCourseProgress() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmCourseProgress> data = realm.where(RealmCourseProgress.class)
                    .isNull("_id").findAll();
            for (RealmCourseProgress sub : data) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/courses_progress", RealmCourseProgress.serializeProgress(sub)).execute().body();
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
            List<RealmFeedback> feedbacks = realm.where(RealmFeedback.class).equalTo("uploaded", false).findAll();
            for (RealmFeedback feedback : feedbacks) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/feedback", RealmFeedback.serializeFeedback(feedback)).execute().body();
                    if (object != null) {
                        feedback.setUploaded(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }, () -> listener.onSuccess("Feedback sync completed successfully"));
    }


    public void uploadUserActivities(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<RealmOfflineActivity> activities = realm.where(RealmOfflineActivity.class)
                    .isNull("_rev").equalTo("type", "login").findAll();
            for (RealmOfflineActivity act : activities) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/login_activities", RealmOfflineActivity.serializeLoginActivities(act)).execute().body();
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
            final RealmResults<RealmRating> activities = realm.where(RealmRating.class).equalTo("isUpdated", true).findAll();
            for (RealmRating act : activities) {
                try {
                    Response<JsonObject> object;
                    if (TextUtils.isEmpty(act.get_id())) {
                        Utilities.log("JSON " + RealmRating.serializeRating(act));
                        object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings", RealmRating.serializeRating(act)).execute();
                    } else {
                        object = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings/" + act.get_id(), RealmRating.serializeRating(act)).execute();
                    }
                    if (object.body() != null) {
                        act.set_id(JsonUtils.getString("_id", object.body()));
                        act.set_rev(JsonUtils.getString("_rev", object.body()));
                        act.setUpdated(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void uploadCrashLog(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmApkLog> logs;
            logs = realm.where(RealmApkLog.class).isNull("_rev").findAll();
            for (RealmApkLog act : logs) {
                try {
                    JsonObject o = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/apk_logs", RealmApkLog.serialize(act)).execute().body();
                    if (o != null) act.set_rev(JsonUtils.getString("_rev", o));
                } catch (IOException e) {
                }
            }
        }, () -> listener.onSuccess("Crash log uploaded."));
    }

    public void uploadResourceActivities(String type) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);

        String db = type.equals("sync") ? "admin_activities" : "resource_activities";
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmResourceActivity> activities;
            if (type.equals("sync")) {
                activities = realm.where(RealmResourceActivity.class).isNull("_rev").equalTo("type", "sync").findAll();
            } else {
                activities = realm.where(RealmResourceActivity.class).isNull("_rev").notEqualTo("type", "sync").findAll();
            }
            for (RealmResourceActivity act : activities) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/" + db, RealmResourceActivity.serializeResourceActivities(act)).execute().body();
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

}
