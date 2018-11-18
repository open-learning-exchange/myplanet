package org.ole.planet.myplanet.Data;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;

import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class realm_myCourses extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
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
        String id = JsonUtils.getString("_id", myCousesDoc);
        realm_myCourses myMyCoursesDB = mRealm.where(realm_myCourses.class).equalTo("id", id).findFirst();
        if (myMyCoursesDB == null) {
            myMyCoursesDB = mRealm.createObject(realm_myCourses.class, id);
        }
        if (!TextUtils.isEmpty(userId)) {
            myMyCoursesDB.setUserId(userId);
        }
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
        realm_courseSteps.insertCourseSteps(myMyCoursesDB.getCourseId(), JsonUtils.getJsonArray("steps", myCousesDoc), JsonUtils.getJsonArray("steps", myCousesDoc).size(), mRealm);
    }

    public static boolean isMyCourse(String userId, Realm realm) {
        realm_myCourses courses = realm.where(realm_myCourses.class).equalTo("userId", userId).findFirst();
        return courses != null;
    }

    public static void insertMyCourses(JsonObject doc, Realm mRealm) {
        insertMyCourses("", doc, mRealm);
    }

    public static realm_myCourses getMyCourse(Realm mRealm, String id) {
        return mRealm.where(realm_myCourses.class).equalTo("courseId", id).findFirst();
    }

    public static void createMyCourse(realm_myCourses course, Realm mRealm, String id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        course.setUserId(id);
        mRealm.commitTransaction();
    }

    public static String[] getMyCourseIds(Realm mRealm, String userId) {
        List<realm_myCourses> list = mRealm.where(realm_myCourses.class).equalTo("userId", userId).findAll();
        String[] myIds = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            myIds[i] = list.get(i).getCourseId();
        }
        return myIds;
    }

    public static JsonArray getMyCourseIds(Realm realm, SharedPreferences sharedPreferences) {
        RealmResults<realm_myCourses> myCourses = realm.where(realm_myCourses.class).isNotEmpty("userId")
                .equalTo("userId", sharedPreferences.getString("userId", "--"), Case.INSENSITIVE).findAll();

        JsonArray ids = new JsonArray();
        for (realm_myCourses lib : myCourses
                ) {
            ids.add(lib.getCourseId());
        }
        return ids;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

}
