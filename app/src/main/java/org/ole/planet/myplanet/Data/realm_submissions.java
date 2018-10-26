package org.ole.planet.myplanet.Data;

import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ole.planet.myplanet.utilities.TimeUtils;
import io.realm.RealmList;
import io.realm.*;

public class realm_submissions extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String parentId;
    private String type;
    private String userId;
    private String user;
    private long date;
    private RealmList<realm_answer> answers;
    private String grade;
    private String status;
    private boolean uploaded;

    public static JsonObject serializeExamResult(Realm mRealm, realm_submissions sub) {
        JsonObject object = new JsonObject();
        realm_UserModel user = mRealm.where(realm_UserModel.class).equalTo("id", sub.userId).findFirst();
        realm_stepExam exam = mRealm.where(realm_stepExam.class).equalTo("id", sub.parentId).findFirst();
        object.addProperty("parentId", sub.getParentId());
        object.addProperty("type", sub.getType());
        object.addProperty("grade", sub.getGrade());
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

    public static String getNoOfSubmissionByUser(String id, String userId, Realm mRealm) {
        return "Survey Taken " + mRealm.where(realm_submissions.class).equalTo("parentId", id).equalTo("userId", userId).findAll().size() + " times";
    }

    public static int getNoOfSurveySubmissionByUser(String userId, Realm mRealm) {
        return mRealm.where(realm_submissions.class).equalTo("userId", userId).equalTo("type", "survey").findAll().size();
    }

    public static String getRecentSubmissionDate(String id, String userId, Realm mRealm) {
        realm_submissions s = mRealm.where(realm_submissions.class).equalTo("parentId", id).equalTo("userId", userId).sort("date", Sort.DESCENDING).findFirst();
        return s == null ? "" : TimeUtils.getFormatedDateWithTime(s.getDate()) + "";
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
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
