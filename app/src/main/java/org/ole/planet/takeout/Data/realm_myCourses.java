package org.ole.planet.takeout.Data;

import com.google.gson.JsonObject;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
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
        return numberOfSteps;
    }

    public void setnumberOfSteps(Integer numberOfSteps) {
        this.numberOfSteps = numberOfSteps;
    }

    public static void insertMyCourses(String userId, String myCoursesID, JsonObject myCousesDoc, Realm mRealm) {
        realm_myCourses myMyCoursesDB = mRealm.createObject(realm_myCourses.class, UUID.randomUUID().toString());
        myMyCoursesDB.setUserId(userId);
        myMyCoursesDB.setCourseId(myCoursesID);
        myMyCoursesDB.setCourse_rev(myCousesDoc.get("_rev").getAsString());
        myMyCoursesDB.setLanguageOfInstruction(myCousesDoc.get("languageOfInstruction").getAsString());
        myMyCoursesDB.setCourseTitle(myCousesDoc.get("courseTitle").getAsString());
        myMyCoursesDB.setMemberLimit(myCousesDoc.get("memberLimit").getAsInt());
        myMyCoursesDB.setDescription(myCousesDoc.get("description").getAsString());
        myMyCoursesDB.setMethod(myCousesDoc.get("method").getAsString());
        myMyCoursesDB.setGradeLevel(myCousesDoc.get("gradeLevel").getAsString());
        myMyCoursesDB.setSubjectLevel(myCousesDoc.get("subjectLevel").getAsString());
        myMyCoursesDB.setCreatedDate(myCousesDoc.get("createdDate").getAsString());
        myMyCoursesDB.setnumberOfSteps(myCousesDoc.get("steps").getAsJsonArray().size());
        realm_courseSteps.insertCourseSteps(myCoursesID, myCousesDoc.get("steps").getAsJsonArray(), myCousesDoc.get("steps").getAsJsonArray().size(), mRealm);
    }

    public static void createFromCourse(realm_courses course, Realm mRealm, String id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realm_myCourses myMyCoursesDB = mRealm.createObject(realm_myCourses.class, UUID.randomUUID().toString());
        myMyCoursesDB.setUserId(id);
        myMyCoursesDB.setCourseId(course.courseId);
        myMyCoursesDB.setCourse_rev(course.course_rev);
        myMyCoursesDB.setLanguageOfInstruction(course.languageOfInstruction);
        myMyCoursesDB.setCourseTitle(course.courseTitle);
        myMyCoursesDB.setMemberLimit(course.memberLimit);
        myMyCoursesDB.setDescription(course.description);
        myMyCoursesDB.setMethod(course.method);
        myMyCoursesDB.setGradeLevel(course.gradeLevel);
        myMyCoursesDB.setSubjectLevel(course.subjectLevel);
        myMyCoursesDB.setCreatedDate(course.createdDate);
        myMyCoursesDB.setnumberOfSteps(course.numberOfSteps);
        mRealm.commitTransaction();
    }
}
