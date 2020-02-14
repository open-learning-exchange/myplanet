package org.ole.planet.myplanet.model;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;

import java.util.List;

import io.realm.Realm;

public class RealmCourseStep extends io.realm.RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String courseId;
    private String stepTitle;
    private String description;
    private Integer noOfResources;
    private Integer noOfExams;

    public static void insertCourseSteps(String myCoursesID, JsonArray steps, int numberOfSteps, Realm mRealm) {
        for (int step = 0; step < numberOfSteps; step++) {
            String step_id = Base64.encodeToString(steps.get(step).toString().getBytes(), Base64.NO_WRAP);
            RealmCourseStep myCourseStepDB = mRealm.where(RealmCourseStep.class).equalTo("id", step_id).findFirst();
            if (myCourseStepDB == null) {
                myCourseStepDB = mRealm.createObject(RealmCourseStep.class, step_id);
            }
            myCourseStepDB.setCourseId(myCoursesID);
            JsonObject stepContainer = steps.get(step).getAsJsonObject();
            myCourseStepDB.setStepTitle(JsonUtils.getString("stepTitle", stepContainer));
            myCourseStepDB.setDescription(JsonUtils.getString("description", stepContainer));
            myCourseStepDB.setNoOfResources(JsonUtils.getJsonArray("resources", stepContainer).size());
            insertCourseStepsAttachments(myCoursesID, step_id, JsonUtils.getJsonArray("resources", stepContainer), mRealm);
            insertExam(stepContainer, mRealm, step_id,step+1, myCoursesID);
        }
    }

    private static void insertExam(JsonObject stepContainer, Realm mRealm, String step_id, int i, String myCoursesID) {
        if (stepContainer.has("exam")){
            JsonObject object = stepContainer.getAsJsonObject("exam");
            object.addProperty("stepNumber", i);
            RealmStepExam.insertCourseStepsExams(myCoursesID, step_id,object , mRealm);
        }
    }

    public static String[] getStepIds(Realm mRealm, String courseId) {
        List<RealmCourseStep> list = mRealm.where(RealmCourseStep.class).equalTo("courseId", courseId).findAll();
        String[] myIds = new String[list.size()];
        int i = 0;
        for (RealmCourseStep c : list) {
            myIds[i] = c.getId();
            i++;
        }
        return myIds;
    }

    public static List<RealmCourseStep> getSteps(Realm mRealm, String courseId) {
        return mRealm.where(RealmCourseStep.class).equalTo("courseId", courseId).findAll();
    }

    public static void insertCourseStepsAttachments(String myCoursesID, String stepId, JsonArray resources, Realm mRealm) {
        for (int i = 0; i < resources.size(); i++) {
            RealmMyLibrary.createStepResource(mRealm, resources.get(i).getAsJsonObject(), myCoursesID, stepId);
        }
    }

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

}
