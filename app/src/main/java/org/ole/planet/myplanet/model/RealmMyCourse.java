package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyCourse extends RealmObject {
    @PrimaryKey
    private String id;
    private RealmList<String> userId;
    private String courseId;
    private String course_rev;
    private String languageOfInstruction;
    private String courseTitle;
    private Integer memberLimit;
    private String description;
    private String method;
    private String gradeLevel;
    private String subjectLevel;
    private String createdDate;
    private Integer numberOfSteps;

    public static void insertMyCourses(String userId, JsonObject myCousesDoc, Realm mRealm) {
        Utilities.log("INSERT COURSE " + new Gson().toJson(myCousesDoc));
        String id = JsonUtils.getString("_id", myCousesDoc);
        RealmMyCourse myMyCoursesDB = mRealm.where(RealmMyCourse.class).equalTo("id", id).findFirst();
        if (myMyCoursesDB == null) {
            myMyCoursesDB = mRealm.createObject(RealmMyCourse.class, id);
        }
        myMyCoursesDB.setUserId(userId);
        myMyCoursesDB.setCourseId(JsonUtils.getString("_id", myCousesDoc));
        myMyCoursesDB.setCourse_rev(JsonUtils.getString("_rev", myCousesDoc));
        myMyCoursesDB.setLanguageOfInstruction(JsonUtils.getString("languageOfInstruction", myCousesDoc));
        myMyCoursesDB.setCourseTitle(JsonUtils.getString("courseTitle", myCousesDoc));
        myMyCoursesDB.setMemberLimit(JsonUtils.getInt("memberLimit", myCousesDoc));
        myMyCoursesDB.setDescription(JsonUtils.getString("description", myCousesDoc));
        myMyCoursesDB.setMethod(JsonUtils.getString("method", myCousesDoc));
        myMyCoursesDB.setGradeLevel(JsonUtils.getString("gradeLevel", myCousesDoc));
        myMyCoursesDB.setSubjectLevel(JsonUtils.getString("subjectLevel", myCousesDoc));
        myMyCoursesDB.setCreatedDate(JsonUtils.getString("createdDate", myCousesDoc));
        myMyCoursesDB.setnumberOfSteps(JsonUtils.getJsonArray("steps", myCousesDoc).size());
        RealmCourseStep.insertCourseSteps(myMyCoursesDB.getCourseId(), JsonUtils.getJsonArray("steps", myCousesDoc), JsonUtils.getJsonArray("steps", myCousesDoc).size(), mRealm);
    }

    public static List<RealmObject> getMyByUserId(Realm mRealm, SharedPreferences settings) {
        RealmResults<RealmMyCourse> libs = mRealm.where(RealmMyCourse.class).findAll();
        List<RealmObject> libraries = new ArrayList<>();
        for (RealmMyCourse item : libs) {
            if (item.getUserId().contains(settings.getString("userId", "--"))) {
                libraries.add(item);
            }
        }
        return libraries;
    }


    public static List<RealmMyCourse> getMyCourseByUserId(String userId, List<RealmMyCourse> libs) {
        List<RealmMyCourse> libraries = new ArrayList<>();
        for (RealmMyCourse item : libs) {
            if (item.getUserId().contains(userId)) {
                libraries.add(item);
            }
        }
        return libraries;
    }

    public static List<RealmMyCourse> getOurCourse(String userId, List<RealmMyCourse> libs) {
        List<RealmMyCourse> libraries = new ArrayList<>();
        for (RealmMyCourse item : libs) {
            if (!item.getUserId().contains(userId)) {
                libraries.add(item);
            }
        }
        return libraries;
    }


    public static boolean isMyCourse(String userId, String courseId, Realm realm) {
        return RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse.class).equalTo("courseId", courseId).findAll()).size() > 0;
    }

    public static void insert(Realm mRealm, JsonObject doc) {
        insertMyCourses("", doc, mRealm);
    }

    public static RealmMyCourse getMyCourse(Realm mRealm, String id) {
        return mRealm.where(RealmMyCourse.class).equalTo("courseId", id).findFirst();
    }

    public static void createMyCourse(RealmMyCourse course, Realm mRealm, String id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        course.setUserId(id);
        mRealm.commitTransaction();
    }
//
//    public static String[] getMyCourseIds(Realm mRealm, String userId) {
//        List<RealmMyCourse> list = mRealm.where(RealmMyCourse.class).equalTo("userId", userId).findAll();
//        String[] myIds = new String[list.size()];
//        for (int i = 0; i < list.size(); i++) {
//            myIds[i] = list.get(i).getCourseId();
//        }
//        return myIds;
//    }

    public static JsonArray getMyCourseIds(Realm realm, String userId) {
        List<RealmMyCourse> myCourses = getMyCourseByUserId(userId, realm.where(RealmMyCourse.class).findAll());
        JsonArray ids = new JsonArray();
        for (RealmObject lib : myCourses
        ) {
            ids.add(((RealmMyCourse) lib).getCourseId());
        }
        return ids;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RealmList<String> getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (this.userId == null) {
            this.userId = new RealmList<>();
        }

        if (!this.userId.contains(userId) && !TextUtils.isEmpty(userId))
            this.userId.add(userId);
    }

    public void removeUserId(String userId) {
        this.userId.remove(userId);
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourse_rev() {
        return course_rev;
    }

    public void setCourse_rev(String course_rev) {
        this.course_rev = course_rev;
    }

    public String getLanguageOfInstruction() {
        return languageOfInstruction;
    }

    public void setLanguageOfInstruction(String languageOfInstruction) {
        this.languageOfInstruction = languageOfInstruction;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public Integer getMemberLimit() {
        return memberLimit;
    }

    public void setMemberLimit(Integer memberLimit) {
        this.memberLimit = memberLimit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(String gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getSubjectLevel() {
        return subjectLevel;
    }

    public void setSubjectLevel(String subjectLevel) {
        this.subjectLevel = subjectLevel;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public Integer getnumberOfSteps() {
        return numberOfSteps == null ? 0 : numberOfSteps;
    }

    public void setnumberOfSteps(Integer numberOfSteps) {
        this.numberOfSteps = numberOfSteps;
    }

    @Override
    public String toString() {
        return courseTitle;
    }
}
