package org.ole.planet.myplanet.model;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.datamanager.ApiInterface;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.Sort;
import okhttp3.Request;
import retrofit2.Response;

public class RealmSubmission extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String parentId;
    private String type;
    private String userId;
    private String user;
    private long startTime;
    private long lastUpdateTime;
    private RealmList<RealmAnswer> answers;
    private long grade;
    private String status;
    private boolean uploaded;
    /// new
    private String sender;
    private String source;
    private String parentCode;
    private String parent;


    public static void insert(Realm mRealm, JsonObject submission) {
        if (submission.has("_attachments")) {
            return;
        }
        String id = JsonUtils.getString("_id", submission);
        RealmSubmission sub = mRealm.where(RealmSubmission.class).equalTo("_id", id).findFirst();
        if (sub == null) {
            sub = mRealm.createObject(RealmSubmission.class, id);
        }
        sub.set_id(JsonUtils.getString("_id", submission));
        sub.setStatus(JsonUtils.getString("status", submission));
        sub.set_rev(JsonUtils.getString("_rev", submission));
        sub.setGrade(JsonUtils.getLong("grade", submission));
        sub.setType(JsonUtils.getString("type", submission));
        sub.setUploaded(JsonUtils.getString("status", submission).equals("graded"));
        sub.setStartTime(JsonUtils.getLong("startTime", submission));
        sub.setLastUpdateTime(JsonUtils.getLong("lastUpdateTime", submission));
        sub.setParentId(JsonUtils.getString("parentId", submission));
        ///
        sub.setSender(JsonUtils.getString("sender", submission));
        sub.setSource(JsonUtils.getString("source", submission));
        sub.setParentCode(JsonUtils.getString("parentCode", submission));
        sub.setParent(new Gson().toJson(JsonUtils.getJsonObject("parent", submission)));
        //

        sub.setUser(new Gson().toJson(JsonUtils.getJsonObject("user", submission)));
//        RealmStepExam exam = mRealm.where(RealmStepExam.class).equalTo("id", JsonUtils.getString("parentId", submission)).findFirst();
        RealmStepExam.insertCourseStepsExams("", "", JsonUtils.getJsonObject("parent", submission), JsonUtils.getString("parentId", submission), mRealm);
        String userId = JsonUtils.getString("_id", JsonUtils.getJsonObject("user", submission));
        if (userId.contains("@")) {
            String[] us = userId.split("@");
            if (us[0].startsWith("org.couchdb.user:")) {
                sub.setUserId(us[0]);
            } else {
                sub.setUserId("org.couchdb.user:" + us[0]);
            }
        } else {
            sub.setUserId(userId);
        }
        Utilities.log("Insert sub " + sub);

    }

    public static JsonObject serializeExamResult(Realm mRealm, RealmSubmission sub, Context context) {
        JsonObject object = new JsonObject();
        RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", sub.getUserId()).findFirst();
        String examId = sub.getParentId();
        if (sub.getParentId().contains("@")) {
            examId = sub.getParentId().split("@")[0];
        }
        RealmStepExam exam = mRealm.where(RealmStepExam.class).equalTo("id", examId).findFirst();
        if (!TextUtils.isEmpty(sub.get_id()))
            object.addProperty("_id", sub.get_id());
        if (!TextUtils.isEmpty(sub.get_rev()))
            object.addProperty("_rev", sub.get_rev());
        object.addProperty("parentId", sub.getParentId());
        object.addProperty("type", sub.getType());
        object.addProperty("grade", sub.getGrade());
        object.addProperty("startTime", sub.getStartTime());
        object.addProperty("lastUpdateTime", sub.getLastUpdateTime());
        object.addProperty("status", sub.getStatus());
        object.addProperty("androidId", NetworkUtils.getMacAddr());
        object.addProperty("deviceName", NetworkUtils.getDeviceName());
        object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        ///
        object.addProperty("sender", sub.getSender());
        object.addProperty("source", sub.getSource());
        object.addProperty("parentCode", sub.getParentCode());
        JsonObject parent = new Gson().fromJson(sub.getParent(), JsonObject.class);
        object.add("parent",parent );
        Utilities.log("Parent " + sub.getParent());
        //
        object.add("answers", RealmAnswer.serializeRealmAnswer(sub.getAnswers()));
        Utilities.log("Parent Exam "  + (exam == null) );
        if (exam != null && parent==null)
            object.add("parent", RealmStepExam.serializeExam(mRealm, exam));
        if (TextUtils.isEmpty(sub.getUser())) {
            object.add("user", user.serialize());
        } else {
            JsonParser parser = new JsonParser();
            object.add("user", parser.parse(sub.getUser()));
        }
        Utilities.log("SerializeExamResult sub " + object);
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

        Utilities.log("Is step completed " + exam.getId() + " " + userId);
        return realm.where(RealmSubmission.class)
                .equalTo("userId", userId)
                .contains("parentId", exam.getId())
                .notEqualTo("status", "pending")
                .findFirst() != null;
    }

    public static RealmSubmission createSubmission(RealmSubmission sub, Realm mRealm) {
        if (sub == null || (sub.getStatus().equals("complete") && sub.getType().equals("exam")))
            sub = mRealm.createObject(RealmSubmission.class, UUID.randomUUID().toString());
        sub.setLastUpdateTime(new Date().getTime());
        return sub;
    }

    public static void continueResultUpload(RealmSubmission sub, ApiInterface apiInterface, Realm realm, Context context) throws IOException {
        JsonObject object = null;
        if (!TextUtils.isEmpty(sub.getUserId()) && sub.getUserId().startsWith("guest"))
            return;
        if (TextUtils.isEmpty(sub.get_id())) {
            object = apiInterface.postDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/submissions", RealmSubmission.serializeExamResult(realm, sub, context)).execute().body();
        } else {
            object = apiInterface.putDoc(Utilities.getHeader(), "application/json", Utilities.getUrl() + "/submissions/" + sub.get_id(), RealmSubmission.serializeExamResult(realm, sub, context)).execute().body();
        }
        if (object != null) {
            sub.set_id(JsonUtils.getString("id", object));
            sub.set_rev(JsonUtils.getString("rev", object));
        }
    }


    public static String getNoOfSubmissionByUser(String id, String userId, Realm mRealm) {
        return "Survey Taken " + mRealm.where(RealmSubmission.class).equalTo("parentId", id).equalTo("userId", userId).findAll().size() + " times";
    }

    public static int getNoOfSurveySubmissionByUser(String userId, Realm mRealm) {
        return mRealm.where(RealmSubmission.class).equalTo("userId", userId).equalTo("type", "survey").equalTo("status", "pending", Case.INSENSITIVE).findAll().size();
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

    public long getGrade() {
        return grade;
    }

    public void setGrade(long grade) {
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

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }


    public static HashMap<String, RealmStepExam> getExamMap(Realm mRealm, List<RealmSubmission> submissions) {
        HashMap<String, RealmStepExam> exams = new HashMap<>();
        for (RealmSubmission sub : submissions) {
            String id = sub.getParentId();
            if (checkParentId(sub.getParentId())) {
                id = sub.getParentId().split("@")[0];
            }
            RealmStepExam survey = mRealm.where(RealmStepExam.class).equalTo("id", id).findFirst();
            if (survey != null)
                exams.put(sub.getParentId(), survey);
        }
        return exams;
    }

    private static boolean checkParentId(String parentId) {
        return parentId != null && parentId.contains("@");
    }

}
