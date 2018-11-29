package org.ole.planet.myplanet.Data;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.Response;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.Sort;

public class realm_submissions extends RealmObject {
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
    private RealmList<realm_answer> answers;
    private String grade;
    private String status;
    private boolean uploaded;


    public static void insertSubmission(Realm mRealm, JsonObject submission) {
        Utilities.log("Insert submission  ");
        String id = JsonUtils.getString("_id", submission);
        realm_submissions sub = mRealm.where(realm_submissions.class).equalTo("id", id).findFirst();
        if (sub == null) {
            sub = mRealm.createObject(realm_submissions.class, id);
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


    public static JsonObject serializeExamResult(Realm mRealm, realm_submissions sub) {
        JsonObject object = new JsonObject();
        realm_UserModel user = mRealm.where(realm_UserModel.class).equalTo("id", sub.userId).findFirst();
        realm_stepExam exam = mRealm.where(realm_stepExam.class).equalTo("id", sub.parentId).findFirst();
        object.addProperty("_id", sub.get_id());
        object.addProperty("parentId", sub.getParentId());
        object.addProperty("type", sub.getType());
        object.addProperty("grade", sub.getGrade());
        object.addProperty("startTime", sub.getStartTime());
        object.addProperty("lastUpdateTime", sub.getLastUpdateTime());
        object.addProperty("localUserImageUri", sub.getLocalUserImageUri());
        object.addProperty("status", sub.getStatus());
        object.add("answers", realm_answer.serializeRealmAnswer(sub.getAnswers()));
        object.add("parent", realm_stepExam.serializeExam(mRealm, exam));
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
        realm_stepExam exam = realm.where(realm_stepExam.class).equalTo("stepId", id).findFirst();
        if (exam == null) {
            return true;
        }
        return realm.where(realm_submissions.class)
                .equalTo("userId", userId)
                .equalTo("parentId", exam.getId())
                .equalTo("status", "graded")
                .findFirst() != null;
    }

    public static realm_submissions createSubmission(realm_submissions sub, List<realm_examQuestion> questions, Realm mRealm) {
        if (sub == null || questions.size() == sub.getAnswers().size())
            sub = mRealm.createObject(realm_submissions.class, UUID.randomUUID().toString());
        return sub;
    }

    public static void continueResultUpload(realm_submissions sub, CouchDbClientAndroid dbClient, Realm realm) {
        Response r;
        if (TextUtils.isEmpty(sub.get_id())) {
            r = dbClient.post(realm_submissions.serializeExamResult(realm, sub));
        } else {
            r = dbClient.update(realm_submissions.serializeExamResult(realm, sub));
        }
        if (!TextUtils.isEmpty(r.getId())) {
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
        return "Survey Taken " + mRealm.where(realm_submissions.class).equalTo("parentId", id).equalTo("userId", userId).findAll().size() + " times";
    }

    public static int getNoOfSurveySubmissionByUser(String userId, Realm mRealm) {
        return mRealm.where(realm_submissions.class).equalTo("userId", userId).equalTo("type", "survey").findAll().size();
    }

    public static String getRecentSubmissionDate(String id, String userId, Realm mRealm) {
        realm_submissions s = mRealm.where(realm_submissions.class).equalTo("parentId", id).equalTo("userId", userId).sort("startTime", Sort.DESCENDING).findFirst();
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

    public RealmList<realm_answer> getAnswers() {
        return answers;
    }

    public void setAnswers(RealmList<realm_answer> answers) {
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
