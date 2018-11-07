package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.lightcouch.Response;
import org.ole.planet.myplanet.Data.realm_feedback;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_myTeams;
import org.ole.planet.myplanet.Data.realm_offlineActivities;
import org.ole.planet.myplanet.Data.realm_resourceActivities;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import okhttp3.ResponseBody;

public class UploadManager {
    private DatabaseService dbService;
    private Context context;
    private CouchDbProperties properties;
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
        Utilities.log("Upload exam result");
        final CouchDbProperties properties = dbService.getClouchDbProperties("submissions", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                List<realm_submissions> submissions = realm.where(realm_submissions.class).equalTo("status", "graded").equalTo("uploaded", false).findAll();
                Utilities.log("Sub size " + submissions.size());
                for (realm_submissions sub : submissions) {
                    Response r = dbClient.post(realm_submissions.serializeExamResult(realm, sub));
                    if (!TextUtils.isEmpty(r.getId())) {
                        sub.setUploaded(true);
                        Utilities.log("ID " + r.getId());
                    }
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                listener.onSuccess("Result sync completed successfully");
            }
        });
    }

    public void uploadFeedback(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        final CouchDbProperties properties = dbService.getClouchDbProperties("feedback", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                List<realm_feedback> feedbacks = realm.where(realm_feedback.class).equalTo("uploaded", false).findAll();
                for (realm_feedback feedback : feedbacks) {
                    Response r = dbClient.post(realm_feedback.serializeFeedback(feedback));
                    if (!TextUtils.isEmpty(r.getId())) {
                        feedback.setUploaded(true);
                    }
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                listener.onSuccess("Feedback sync completed successfully");
            }
        });
    }

    public void uploadToshelf(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        final CouchDbProperties properties = dbService.getClouchDbProperties("shelf", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                JsonObject object = getShelfData(realm);
                try {
                    JsonObject d = dbClient.find(JsonObject.class, sharedPreferences.getString("userId", ""));
                    object.addProperty("_rev", d.get("_rev").getAsString());
                    Response r = dbClient.update(object);
                } catch (Exception e) {
                    listener.onSuccess("Unable to update documents.");
                }

            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                listener.onSuccess("Sync with server completed successfully");
            }
        });
    }

    public void uploadUserActivities(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        final CouchDbProperties properties = dbService.getClouchDbProperties("login_activities", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                final RealmResults<realm_offlineActivities> activities = realm.where(realm_offlineActivities.class)
                        .isNull("_rev").equalTo("type", "login").findAll();
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                for (realm_offlineActivities act : activities) {
                    Response r = dbClient.post(realm_offlineActivities.serializeLoginActivities(act));
                    act.changeRev(r);
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                listener.onSuccess("Sync with server completed successfully");
            }
        });
    }

    public void uploadResourceActivities(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        final CouchDbProperties properties = dbService.getClouchDbProperties("resource_activities", sharedPreferences);
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                final RealmResults<realm_resourceActivities> activities = realm.where(realm_resourceActivities.class)
                        .isNull("_rev").findAll();
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                for (realm_resourceActivities act : activities) {
                    Response r = dbClient.post(realm_resourceActivities.serializeResourceActivities(act));
                    if (!TextUtils.isEmpty(r.getId())) {
                        act.set_rev(r.getRev());
                        act.set_id(r.getId());
                    }
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
