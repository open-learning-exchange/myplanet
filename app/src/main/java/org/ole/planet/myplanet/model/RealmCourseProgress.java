package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.PrimaryKey;

public class RealmCourseProgress extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String createdOn;
    private long createdDate;
    private long updatedDate;

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
        object.addProperty("createdDate", progress.getCreatedDate());
        object.addProperty("updatedDate", progress.getUpdatedDate());
        return object;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public static HashMap<String, JsonObject> getCourseProgress(Realm mRealm, String userId) {
        List<RealmMyCourse> r = RealmMyCourse.getMyCourseByUserId(userId, mRealm.where(RealmMyCourse.class).findAll());

        HashMap<String, JsonObject> map = new HashMap<>();
        for (RealmMyCourse course : r) {
            JsonObject object = new JsonObject();
            List<RealmCourseStep> steps = RealmCourseStep.getSteps(mRealm, course.getCourseId());
            object.addProperty("max", steps.size());

            object.addProperty("current", getCurrentProgress(steps, mRealm, userId, course.getCourseId()));
            if (RealmMyCourse.isMyCourse(userId, course.getCourseId(), mRealm))
                map.put(course.getCourseId(), object);
        }
        return map;
    }

    public static List<RealmSubmission> getPassedCourses(Realm mRealm, String userId) {

        RealmResults<RealmCourseProgress> progresses = mRealm.where(RealmCourseProgress.class).equalTo("userId", userId).equalTo("passed", true).findAll();
        List<RealmSubmission> list = new ArrayList<>();
        for (RealmCourseProgress progress : progresses) {
//            if (RealmCertification.isCourseCertified(mRealm, progress.getCourseId())) {
            Utilities.log("Course id  certified " + progress.getCourseId());
            RealmSubmission sub = mRealm.where(RealmSubmission.class).contains("parentId", progress.getCourseId()).equalTo("userId", userId).sort("lastUpdateTime", Sort.DESCENDING).findFirst();
            if (sub != null)
                list.add(sub);
//            }
        }
        return list;
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

    public static void insert(Realm mRealm, JsonObject act) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        Utilities.log("insert course progresss " + new Gson().toJson(act));
        RealmCourseProgress courseProgress = mRealm.where(RealmCourseProgress.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (courseProgress == null)
            courseProgress = mRealm.createObject(RealmCourseProgress.class, JsonUtils.getString("_id", act));
        courseProgress.set_rev(JsonUtils.getString("_rev", act));
        courseProgress.set_id(JsonUtils.getString("_id", act));
        courseProgress.setPassed(JsonUtils.getBoolean("passed", act));
        courseProgress.setStepNum(JsonUtils.getInt("stepNum", act));
        courseProgress.setUserId(JsonUtils.getString("userId", act));
        courseProgress.setParentCode(JsonUtils.getString("parentCode", act));
        courseProgress.setCourseId(JsonUtils.getString("courseId", act));
        courseProgress.setCreatedOn(JsonUtils.getString("createdOn", act));
        courseProgress.setCreatedDate(JsonUtils.getLong("createdDate", act));
        courseProgress.setUpdatedDate(JsonUtils.getLong("updatedDate", act));
        mRealm.commitTransaction();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    public boolean isPassed() {
        return passed;
    }
}
