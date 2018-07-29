package org.ole.planet.takeout.Data;

import com.google.gson.JsonObject;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_courses extends RealmObject {
    @PrimaryKey
    public String id;
    public String courseId;
    public String course_rev;
    public String languageOfInstruction;
    public String courseTitle;
    public Integer memberLimit;
    public String description;
    public String method;
    public String gradeLevel;
    public String subjectLevel;
    public String createdDate;
    public Integer numberOfSteps;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }


    public void setCourse_rev(String course_rev) {
        this.course_rev = course_rev;
    }


    public void setLanguageOfInstruction(String languageOfInstruction) {
        this.languageOfInstruction = languageOfInstruction;
    }


    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }


    public void setMemberLimit(Integer memberLimit) {
        this.memberLimit = memberLimit;
    }


    public void setDescription(String description) {
        this.description = description;
    }


    public void setMethod(String method) {
        this.method = method;
    }


    public void setGradeLevel(String gradeLevel) {
        this.gradeLevel = gradeLevel;
    }


    public void setSubjectLevel(String subjectLevel) {
        this.subjectLevel = subjectLevel;
    }


    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }


    public void setnumberOfSteps(Integer numberOfSteps) {
        this.numberOfSteps = numberOfSteps;
    }

    public static void insertMyCourses(JsonObject myCousesDoc, Realm mRealm) {
        realm_courses myMyCoursesDB = mRealm.createObject(realm_courses.class, UUID.randomUUID().toString());
        myMyCoursesDB.setCourseId(myCousesDoc.get("_id").getAsString());
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
        realm_courseSteps.insertCourseSteps(myMyCoursesDB.getId(), myCousesDoc.get("steps").getAsJsonArray(), myCousesDoc.get("steps").getAsJsonArray().size(), mRealm);
    }
}
