package org.ole.planet.takeout.Data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.takeout.utilities.TimeUtils;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.List;

import io.realm.RealmList;
import io.realm.*;

public class realm_submissions extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String parentId;
    private String type;
    private String userId;
    private long date;
    private RealmList<realm_answer> answers;
    private String grade;
    private String status;
    private boolean uploaded;

    public static JsonArray serializeExamResult(Realm mRealm){
        JsonArray array = new JsonArray();
        List<realm_submissions> submissions = mRealm.where(realm_submissions.class).equalTo("type", "exam").findAll();
        for (int i = 0 ; i < submissions.size() ; i ++){
            JsonObject object = new JsonObject();
            object.addProperty("", "");
        }

        return  array;

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
}
