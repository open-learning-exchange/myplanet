package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.datamanager.ApiClient;
import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.FileUploadService;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmApkLog;
import org.ole.planet.myplanet.model.RealmCourseActivity;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmNewsLog;
import org.ole.planet.myplanet.model.RealmOfflineActivity;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmResourceActivity;
import org.ole.planet.myplanet.model.RealmSearchActivity;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmSubmitPhotos;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UploadManager extends FileUploadService {
    private static UploadManager instance;
    Context context;
    SharedPreferences pref;
    private DatabaseService dbService;
    private Realm mRealm;

    public UploadManager(Context context) {
        dbService = new DatabaseService(context);
        this.context = context;
        pref = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static UploadManager getInstance() {
        if (instance == null) {
            instance = new UploadManager(MainApplication.context);
        }
        return instance;
    }


    public void uploadNewsActivities() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmNewsLog> newsLog = realm.where(RealmNewsLog.class).isNull("_id").or().isEmpty("_id").findAll();

            for (RealmNewsLog news : newsLog) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/myplanet_activities", RealmNewsLog.serialize(news)).execute().body();
                    Utilities.log("Team upload " + new Gson().toJson(object));
                    if (object != null) {
                        news.set_id(JsonUtils.getString("id", object));
                        news.set_rev(JsonUtils.getString("rev", object));
                    }
                } catch (IOException e) {
                }

            }
        });
    }

    public void uploadActivities(SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        RealmUserModel model = new UserProfileDbHandler(MainApplication.context).getUserModel();
        if (model == null)
            return;
        if (model.isManager())
            return;
        try {
            apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/myplanet_activities", MyPlanet.getNormalMyPlanetActivities(MainApplication.context, pref, model)).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                }
            });
            apiInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/myplanet_activities/" + VersionUtils.getAndroidId(MainApplication.context) + "@" + NetworkUtils.getMacAddr()).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    JsonObject object = response.body();
                    if (object != null) {
                        JsonArray usages = object.getAsJsonArray("usages");
                        usages.addAll(MyPlanet.getTabletUsages(context, pref));
                        object.add("usages", usages);
                    } else {
                        object = MyPlanet.getMyPlanetActivities(context, pref, model);
                    }
                    apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/myplanet_activities", object).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                            if (listener != null) {
                                listener.onSuccess("My planet activities uploaded successfully");
                            }
                        }

                        @Override
                        public void onFailure(Call<JsonObject> call, Throwable t) {
                        }
                    });
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                }
            });
        } catch (Exception e) {
        }
    }


    public void uploadExamResult(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<RealmSubmission> submissions = realm.where(RealmSubmission.class).findAll();
            for (RealmSubmission sub : submissions) {
                try {
                    if (sub.getAnswers().size() > 0) {
                        RealmSubmission.continueResultUpload(sub, apiInterface, realm, context);
                    }
                } catch (Exception e) {
                    Utilities.log("Upload exam result");
                }
            }
        }, () -> listener.onSuccess("Result sync completed successfully"), (e) -> {
            e.printStackTrace();
        });
        uploadCourseProgress();
    }


    public JsonObject createImage(RealmUserModel user, JsonObject imgObject) {
        JsonObject object = new JsonObject();
        object.addProperty("title", JsonUtils.getString("fileName", imgObject));
        object.addProperty("createdDate", new Date().getTime());
        object.addProperty("filename", JsonUtils.getString("fileName", imgObject));
        object.addProperty("addedBy", user.getId());
        object.addProperty("private", true);
        object.addProperty("resideOn", user.getParentCode());
        object.addProperty("sourcePlanet", user.getPlanetCode());
        JsonObject object1 = new JsonObject();
        object.addProperty("androidId", NetworkUtils.getMacAddr());
        object.addProperty("deviceName", NetworkUtils.getDeviceName());
        object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context));
        object.add("privateFor", object1);
        object.addProperty("mediaType", "image");
        return object;
    }

    public void uploadAchievement() {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<RealmAchievement> list = realm.where(RealmAchievement.class).findAll();
            for (RealmAchievement sub : list) {
                try {
                    if (sub.get_id().startsWith("guest"))
                        continue;
                    JsonObject ob = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/achievements/" + sub.get_id(), RealmAchievement.serialize(sub)).execute().body();
                    if (ob == null) {
                        ResponseBody re = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/achievements", RealmAchievement.serialize(sub)).execute().errorBody();
                    }
                } catch (IOException e) {
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
                    if (sub.getUserId().startsWith("guest"))
                        continue;
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/courses_progress", RealmCourseProgress.serializeProgress(sub)).execute().body();
                    if (object != null) {
                        sub.set_id(JsonUtils.getString("id", object));
                        sub.set_rev(JsonUtils.getString("rev", object));
                    }
                } catch (IOException e) {
                }
            }
        });
    }


    public void uploadFeedback(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmFeedback> feedbacks = realm.where(RealmFeedback.class).findAll();
            for (RealmFeedback feedback : feedbacks) {
                try {
                    Response res;
                    res = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/feedback", RealmFeedback.serializeFeedback(feedback)).execute();
                    if (res.body() != null) {
                        Utilities.log(new Gson().toJson(res.body()));
                        JsonObject r = (JsonObject) res.body();
                        feedback.set_rev(r.get("rev").getAsString());
                        feedback.set_id(r.get("id").getAsString());
                    } else {
                        Utilities.log("ERRRRRRRR " + res.errorBody().string());
                    }
                } catch (IOException e) {
                }

            }
        }, () -> listener.onSuccess("Feedback sync completed successfully"));
    }


    public void uploadSubmitPhotos(SuccessListener listener) {
        mRealm = new DatabaseService(context).getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
                    List<RealmSubmitPhotos> data = realm.where(RealmSubmitPhotos.class).equalTo("uploaded", false).findAll();
                    for (RealmSubmitPhotos sub : data) {
                        try {
                            JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/submissions", RealmSubmitPhotos.serializeRealmSubmitPhotos(sub)).execute().body();
                            if (object != null) {
                                String _rev = JsonUtils.getString("rev", object);
                                String _id = JsonUtils.getString("id", object);
                                sub.setUploaded(true);
                                sub.set_rev(_rev);
                                sub.set_id(_id);
                                uploadAttachment(_id, _rev, sub, listener);
                                Utilities.log("Submitting photos to Realm");
                            }
                        } catch (Exception e) {
                        }
                    }
                }
        );

    }

    public void uploadResource(SuccessListener listener) {
        mRealm = new DatabaseService(context).getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
                    RealmUserModel user = realm.where(RealmUserModel.class).equalTo("id", pref.getString("userId", "")).findFirst();
                    List<RealmMyLibrary> data = realm.where(RealmMyLibrary.class).isNull("_rev").findAll();
                    for (RealmMyLibrary sub : data) {
                        try {
                            JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/resources", RealmMyLibrary.serialize(sub, user)).execute().body();
                            if (object != null) {
                                String _rev = JsonUtils.getString("rev", object);
                                String _id = JsonUtils.getString("id", object);
                                sub.set_rev(_rev);
                                sub.set_id(_id);
                                uploadAttachment(_id, _rev, sub, listener);
                                Utilities.log("Submitting resources to Realm");
                            }
                        } catch (Exception e) {
                        }
                    }
                }
        );

    }


    public void uploadMyPersonal(RealmMyPersonal personal, SuccessListener listener) {
        mRealm = new DatabaseService(context).getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        if (!personal.isUploaded()) {
            apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/resources", RealmMyPersonal.serialize(personal, context)).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    JsonObject object = response.body();
                    if (object != null) {
                        if (!mRealm.isInTransaction())
                            mRealm.beginTransaction();
                        String _rev = JsonUtils.getString("rev", object);
                        String _id = JsonUtils.getString("id", object);
                        personal.setUploaded(true);
                        personal.set_rev(_rev);
                        personal.set_id(_id);
                        mRealm.commitTransaction();
                        uploadAttachment(_id, _rev, personal, listener);
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    listener.onSuccess("Unable to upload resource");
                }
            });
        }
    }


    public void uploadTeamTask() {
        mRealm = new DatabaseService(context).getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            List<RealmTeamTask> list = realm.where(RealmTeamTask.class).findAll();
            for (RealmTeamTask task : list) {
                if (TextUtils.isEmpty(task.get_id()) || task.isUpdated()) {
                    JsonObject object = null;
                    try {
                        object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/tasks", RealmTeamTask.serialize(realm, task)).execute().body();
                        if (object != null) {
                            String _rev = JsonUtils.getString("rev", object);
                            String _id = JsonUtils.getString("id", object);
                            task.set_rev(_rev);
                            task.set_id(_id);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
    }


    public void uploadTeams() {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        mRealm.executeTransactionAsync(realm -> {
            List<RealmMyTeam> teams = realm.where(RealmMyTeam.class).equalTo("updated", true).findAll();
            Utilities.log("Teams size " + teams.size());
            for (RealmMyTeam team : teams) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/teams", RealmMyTeam.serialize(team)).execute().body();
                    Utilities.log("Team upload " + new Gson().toJson(object));
                    if (object != null) {
//                        team.set_id(JsonUtils.getString("id", object));
                        team.set_rev(JsonUtils.getString("rev", object));
                        team.setUpdated(false);
                    }
                } catch (IOException e) {
                }

            }
        });
    }


    public void uploadUserActivities(final SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm = dbService.getRealmInstance();
        RealmUserModel model = new UserProfileDbHandler(MainApplication.context).getUserModel();
        if (model == null)
            return;
        if (model.isManager())
            return;
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<RealmOfflineActivity> activities = realm.where(RealmOfflineActivity.class)
                    .isNull("_rev").equalTo("type", "login").findAll();
            for (RealmOfflineActivity act : activities) {
                try {
                    if (act.getUserId().startsWith("guest"))
                        continue;
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/login_activities", RealmOfflineActivity.serializeLoginActivities(act, context)).execute().body();
                    act.changeRev(object);
                } catch (IOException e) {
                }

            }
            uploadTeamActivities(realm, apiInterface);
        }, () -> {
            listener.onSuccess("Sync with server completed successfully");
        }, (e) -> listener.onSuccess(e.getMessage()));
    }

    private void uploadTeamActivities(Realm realm, ApiInterface apiInterface) {
        final RealmResults<RealmTeamLog> logs = realm.where(RealmTeamLog.class).isNull("_rev")
                .findAll();
        for (RealmTeamLog log : logs) {
            try {
                JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/team_activities", RealmTeamLog.serializeTeamActivities(log, context)).execute().body();
                if (object != null) {
                    log.set_id(JsonUtils.getString("id", object));
                    log.set_rev(JsonUtils.getString("rev", object));
                }
            } catch (IOException e) {
            }
        }
        Utilities.log("Upload team activities");


    }

    public void uploadRating(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<RealmRating> activities = realm.where(RealmRating.class).equalTo("isUpdated", true).findAll();
            for (RealmRating act : activities) {
                try {
                    if (act.getUserId().startsWith("guest"))
                        continue;
                    Response<JsonObject> object;
                    if (TextUtils.isEmpty(act.get_id())) {
                        object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings", RealmRating.serializeRating(act)).execute();
                    } else {
                        object = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/ratings/" + act.get_id(), RealmRating.serializeRating(act)).execute();
                    }
                    if (object.body() != null) {
                        act.set_id(JsonUtils.getString("id", object.body()));
                        act.set_rev(JsonUtils.getString("rev", object.body()));
                        act.setUpdated(false);
                    }
                } catch (Exception e) {
                }
            }
        });
    }


    public void uploadNews() {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        RealmUserModel userModel = new UserProfileDbHandler(context).getUserModel();
        mRealm.executeTransactionAsync(realm -> {
            final RealmResults<RealmNews> activities = realm.where(RealmNews.class).findAll();
            for (RealmNews act : activities) {
                try {
                    if (act.getUserId().startsWith("guest"))
                        continue;
                    JsonObject object = RealmNews.serializeNews(act, userModel);
                    JsonArray image = act.getImagesArray();
                    RealmUserModel user = realm.where(RealmUserModel.class).equalTo("id", pref.getString("userId", "")).findFirst();
                    if (act.getImageUrls() != null) {
                        for (String imageobject : act.getImageUrls()) {
                            JsonObject imgObject = new Gson().fromJson(imageobject, JsonObject.class);
                            JsonObject ob = createImage(user, imgObject);
                            JsonObject response = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/resources", ob).execute().body();
                            String _rev = JsonUtils.getString("rev", response);
                            String _id = JsonUtils.getString("id", response);
                            File f = new File(JsonUtils.getString("imageUrl", imgObject));
                            Utilities.log("IMAGE FILE URL  " + JsonUtils.getString("imageUrl", imgObject));
                            String name = FileUtils.getFileNameFromUrl(JsonUtils.getString("imageUrl", imgObject));
                            String format = "%s/resources/%s/%s";
                            URLConnection connection = f.toURL().openConnection();
                            String mimeType = connection.getContentType();
                            RequestBody body = RequestBody.create(MediaType.parse("application/octet"), FileUtils.fullyReadFileToBytes(f));
                            String url = String.format(format, Utilities.getUrl(), _id, name);
                            Response<JsonObject> res = apiInterface.uploadResource(getHeaderMap(mimeType, _rev), url, body).execute();
                            JsonObject attachment = res.body();
                            JsonObject resourceObject = new JsonObject();
                            resourceObject.addProperty("resourceId", JsonUtils.getString("id", attachment));
                            resourceObject.addProperty("filename", JsonUtils.getString("fileName", imgObject));
                            String markdown = "![](resources/" + JsonUtils.getString("id", attachment) + "/" + JsonUtils.getString("fileName", imgObject) + ")";
                            resourceObject.addProperty("markdown", markdown);
                            String msg = JsonUtils.getString("message", object);
                            msg += "\n" + markdown;
                            object.addProperty("message", msg);
                            image.add(resourceObject);
                        }
                    }
                    act.setImages(new Gson().toJson(image));
                    object.add("images", image);
                    Response<JsonObject> newsUploadResponse;
                    if (TextUtils.isEmpty(act.get_id())) {
                        newsUploadResponse = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/news", object).execute();
                    } else {
                        newsUploadResponse = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/news/" + act.get_id(), object).execute();
                    }
                    if (newsUploadResponse.body() != null) {
                        act.getImageUrls().clear();
                        act.set_id(JsonUtils.getString("id", newsUploadResponse.body()));
                        act.set_rev(JsonUtils.getString("rev", newsUploadResponse.body()));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        uploadNewsActivities();
    }


    public void uploadCrashLog(final SuccessListener listener) {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmApkLog> logs;
            logs = realm.where(RealmApkLog.class).isNull("_rev").findAll();
            for (RealmApkLog act : logs) {
                try {
                    JsonObject o = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/apk_logs", RealmApkLog.serialize(act, context)).execute().body();
                    if (o != null) act.set_rev(JsonUtils.getString("rev", o));
                } catch (IOException e) {
                }
            }
        }, () -> listener.onSuccess("Crash log uploaded."));
    }


    public void uploadSearchActivity() {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmSearchActivity> logs;
            logs = realm.where(RealmSearchActivity.class).isEmpty("_rev").findAll();
            for (RealmSearchActivity act : logs) {
                try {
                    JsonObject o = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/search_activities", act.serialize()).execute().body();
                    if (o != null) act.set_rev(JsonUtils.getString("rev", o));
                } catch (IOException e) {
                }
            }
        });
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
                        act.set_rev(JsonUtils.getString("rev", object));
                        act.set_id(JsonUtils.getString("id", object));
                    }
                } catch (IOException e) {
                }
            }
        });
    }


    public void uploadCourseActivities() {
        mRealm = dbService.getRealmInstance();
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        mRealm.executeTransactionAsync(realm -> {
            RealmResults<RealmCourseActivity> activities;
            activities = realm.where(RealmCourseActivity.class).isNull("_rev").notEqualTo("type", "sync").findAll();
            for (RealmCourseActivity act : activities) {
                try {
                    JsonObject object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/course_activities", RealmCourseActivity.serializeSerialize(act)).execute().body();
                    if (object != null) {
                        act.set_rev(JsonUtils.getString("rev", object));
                        act.set_id(JsonUtils.getString("id", object));
                    }
                } catch (IOException e) {
                }
            }
        });
    }
}
