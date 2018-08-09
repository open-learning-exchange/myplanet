package org.ole.planet.takeout.Data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;

import java.util.List;
import java.util.UUID;

import io.realm.Realm;

public class realm_courseSteps extends io.realm.RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String courseId;
    private String stepTitle;
    private String description;
    private Integer noOfResources;
    private Integer noOfExams;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getStepTitle() {
        return stepTitle;
    }

    public void setStepTitle(String stepTitle) {
        this.stepTitle = stepTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getNoOfResources() {
        return noOfResources;
    }

    public void setNoOfResources(int noOfResources) {
        this.noOfResources = noOfResources;
    }

    public Integer getNoOfExams() {
        return noOfExams;
    }

    public void setNoOfExams(int noOfExams) {
        this.noOfExams = noOfExams;
    }

    public static void insertCourseSteps(String myCoursesID, JsonArray steps, int numberOfSteps, Realm mRealm) {
        for (int step = 0; step < numberOfSteps; step++) {
            String step_id = UUID.randomUUID().toString();
            realm_courseSteps myCourseStepDB = mRealm.createObject(realm_courseSteps.class, step_id);
            myCourseStepDB.setCourseId(myCoursesID);
            JsonObject stepContainer = steps.get(step).getAsJsonObject();
            myCourseStepDB.setStepTitle(stepContainer.get("stepTitle").getAsString());
            myCourseStepDB.setDescription(stepContainer.get("description").getAsString());
            if (stepContainer.has("resources")) {
                myCourseStepDB.setNoOfResources(stepContainer.get("resources").getAsJsonArray().size());
                insertCourseStepsAttachments(myCoursesID, step_id, stepContainer.getAsJsonArray("resources"), mRealm);
            }
            // myCourseStepDB.setNoOfResources(stepContainer.get("exam").getAsJsonArray().size());
            if (stepContainer.has("exam"))
                realm_stepExam.insertCourseStepsExams(myCoursesID, step_id, stepContainer.getAsJsonObject("exam"), mRealm);
        }
    }

    public static String[] getStepIds(Realm mRealm, String courseId) {
        List<realm_courseSteps> list = mRealm.where(realm_courseSteps.class).equalTo("courseId", courseId).findAll();
        String[] myIds = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            myIds[i] = list.get(i).getId();
        }
        return myIds;
    }

    public static List<realm_courseSteps> getSteps(Realm mRealm, String courseId) {
        return mRealm.where(realm_courseSteps.class).equalTo("courseId", courseId).findAll();
    }


    public static void insertCourseStepsAttachments(String myCoursesID, String stepId, JsonArray resources, Realm mRealm) {
        for (int i = 0; i < resources.size(); i++) {
            JsonObject res = resources.get(i).getAsJsonObject();
            realm_myLibrary.createStepResource(mRealm, res, myCoursesID, stepId);
        }
    }

}
