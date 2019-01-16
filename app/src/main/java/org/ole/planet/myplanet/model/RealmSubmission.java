package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.Sort;

public class RealmSubmission extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String parentId;
    private String type;
    private String userId;
    private String user;
    private String localUserImageUri;
    private long startTime;
    private long lastUpdateTime;
    private RealmList<RealmAnswer> answers;
    private String grade;
    private String status;
    private boolean uploaded;


    public static void insertSubmission(Realm mRealm, JsonObject submission) {
        Utilities.log("Insert submission  ");
        String id = JsonUtils.getString("_id", submission);
        RealmSubmission sub = mRealm.where(RealmSubmission.class).equalTo("id", id).findFirst();
        if (sub == null) {
            sub = mRealm.createObject(RealmSubmission.class, id);
        }
        sub.set_id(JsonUtils.getString("_id", submission));
        sub.setStatus(JsonUtils.getString("status", submission));
        sub.set_rev(JsonUtils.getString("_rev", submission));
        sub.setGrade(JsonUtils.getString("grade", submission));
        sub.setType(JsonUtils.getString("type", submission));
        sub.setUploaded(JsonUtils.getString("status", submission).equals("graded"));
        sub.setStartTime(JsonUtils.getLong("startTime", submission));
        sub.setLastUpdateTime(JsonUtils.getLong("lastUpdateTime", submission));
        sub.setParentId(JsonUtils.getString("parentId", submission));
        sub.setUser(new Gson().toJson(JsonUtils.getJsonObject("user", submission)));
        sub.setUserId(JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission)));
    }


    public static JsonObject serializeExamResult(Realm mRealm, RealmSubmission sub) {
        JsonObject object = new JsonObject();
        RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", sub.userId).findFirst();
        RealmStepExam exam = mRealm.where(RealmStepExam.class).equalTo("id", sub.parentId).findFirst();
        object.addProperty("_id", sub.get_id());
        object.addProperty("parentId", sub.getParentId());
        object.addProperty("type", sub.getType());
        object.addProperty("grade", sub.getGrade());
        object.addProperty("startTime", sub.getStartTime());
        object.addProperty("lastUpdateTime", sub.getLastUpdateTime());
        object.addProperty("localUserImageUri", sub.getLocalUserImageUri());
        object.addProperty("status", sub.getStatus());
        object.add("answers", RealmAnswer.serializeRealmAnswer(sub.getAnswers()));
        object.add("parent", RealmStepExam.serializeExam(mRealm, exam));
        if (TextUtils.isEmpty(sub.getUser())) {
            object.add("user", user.serialize());
        } else {
            JsonParser parser = new JsonParser();
            object.add("user", parser.parse(sub.getUser()));
        }
        return object;

    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public static boolean isStepCompleted(Realm realm, String id, String userId) {
        RealmStepExam exam = realm.where(RealmStepExam.class).equalTo("stepId", id).findFirst();
        if (exam == null) {
            return true;
        }
        return realm.where(RealmSubmission.class)
                .equalTo("userId", userId)
                .equalTo("parentId", exam.getId())
                .equalTo("status", "graded")
                .findFirst() != null;
    }

    public static RealmSubmission createSubmission(RealmSubmission sub, List<RealmExamQuestion> questions, Realm mRealm) {
        if (sub == null || questions.size() == sub.getAnswers().size())
            sub = mRealm.createObject(RealmSubmission.class, UUID.randomUUID().toString());
        return sub;
    }

    public static void continueResultUpload(RealmSubmission sub, ApiInterface apiInterface, Realm realm) throws IOException {
        JsonObject object;
        if (TextUtils.isEmpty(sub.get_id())) {
            object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/submissions", RealmSubmission.serializeExamResult(realm, sub)).execute().body();
        } else {
            object = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/submissions/" + sub.get_id(), RealmSubmission.serializeExamResult(realm, sub)).execute().body();
        }
        if (object != null) {
            sub.setUploaded(true);
        }
    }

    public String getLocalUserImageUri() {
        return localUserImageUri;
    }

    public void setLocalUserImageUri(String localUserImageUri) {
        this.localUserImageUri = localUserImageUri;
    }

    public static String getNoOfSubmissionByUser(String id, String userId, Realm mRealm) {
        return "Survey Taken " + mRealm.where(RealmSubmission.class).equalTo("parentId", id).equalTo("userId", userId).findAll().size() + " times";
    }

    public static int getNoOfSurveySubmissionByUser(String userId, Realm mRealm) {
        return mRealm.where(RealmSubmission.class).equalTo("userId", userId).equalTo("type", "survey").findAll().size();
    }

    public static String getRecentSubmissionDate(String id, String userId, Realm mRealm) {
        RealmSubmission s = mRealm.where(RealmSubmission.class).equalTo("parentId", id).equalTo("userId", userId).sort("startTime", Sort.DESCENDING).findFirst();
        return s == null ? "" : TimeUtils.getFormatedDateWithTime(s.getStartTime()) + "";
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public RealmList<RealmAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(RealmList<RealmAnswer> answers) {
        this.answers = answers;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
