package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmCourseProgress extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private long createdOn;

    private String _rev;

    private int stepNum;

    private boolean passed;

    private String userId;

    private String courseId;

    private String parentCode;

    public static JsonObject serializeProgress(RealmCourseProgress progress) {
        JsonObject object = new JsonObject();
        object.addProperty("userId", progress.getUserId());
        object.addProperty("parentCode", progress.getParentCode());
        object.addProperty("courseId", progress.getCourseId());
        object.addProperty("passed", progress.getPassed());
        object.addProperty("stepNum", progress.getStepNum());
        object.addProperty("createdOn", progress.getCreatedOn());
        return object;
    }

    public static HashMap<String, JsonObject> getCourseProgress(Realm mRealm, String userId) {
        List<RealmMyCourse> r = RealmMyCourse.getMyCourseByUserId(userId, mRealm.where(RealmMyCourse.class).findAll());

        HashMap<String, JsonObject> map = new HashMap<>();
        for (RealmMyCourse course : r) {
            JsonObject object = new JsonObject();
            List<RealmCourseStep> steps = RealmCourseStep.getSteps(mRealm, course.getCourseId());
            object.addProperty("max", steps.size());

            object.addProperty("current", getCurrentProgress(steps, mRealm,userId, course.getCourseId()));
            if (RealmMyCourse.isMyCourse(userId, course.getCourseId(), mRealm))
                map.put(course.getCourseId(), object);
        }
        return map;
    }

    public static int getCurrentProgress(List<RealmCourseStep> steps, Realm mRealm, String userId, String courseId) {
        int i;
        for (i = 0; i < steps.size(); i++) {
            RealmCourseProgress progress = mRealm.where(RealmCourseProgress.class).equalTo("stepNum", i + 1).equalTo("userId", userId).equalTo("courseId", courseId).findFirst();
            if (progress == null) {
                break;
            }
        }
        return i;
    }


    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public int getStepNum() {
        return stepNum;
    }

    public void setStepNum(int stepNum) {
        this.stepNum = stepNum;
    }

    public boolean getPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }
//
//    public static void insertCourseProgress(Realm mRealm, JsonObject act) {
//        RealmCourseProgress courseProgress = mRealm.where(RealmCourseProgress.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
//        if (courseProgress == null)
//            courseProgress = mRealm.createObject(RealmCourseProgress.class, JsonUtils.getString("_id", act));
//        courseProgress.set_rev(JsonUtils.getString("_rev", act));
//        courseProgress.set_id(JsonUtils.getString("_id", act));
//        courseProgress.setPassed(JsonUtils.getString("passed", act));
//        courseProgress.setStepNum(JsonUtils.getString("stepNum", act));
//        courseProgress.setUserId(JsonUtils.getString("userId", act));
//        courseProgress.setParentCode(JsonUtils.getString("parentCode", act));
//        courseProgress.setCreatedOn(JsonUtils.getString("createdOn", act));
//    }

}
