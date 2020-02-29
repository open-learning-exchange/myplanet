package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmStepExam extends RealmObject {
    @PrimaryKey
    private String id;
    private String _rev;
    private long createdDate;
    private long updatedDate;
    private String createdBy;
    private int totalMarks;
    private String name;
    private String type;
    private String stepId;
    private String courseId;
    private String sourcePlanet;
    private String passingPercentage;
    private int noOfQuestions;
    private boolean isFromNation;

    public static void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam, Realm mRealm) {
        insertCourseStepsExams(myCoursesID, step_id, exam,"", mRealm);
    }

    public static void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam, String parentId, Realm mRealm) {
        RealmStepExam myExam = mRealm.where(RealmStepExam.class).equalTo("id", JsonUtils.getString("_id", exam)).findFirst();
        if (myExam == null) {
            String id = JsonUtils.getString("_id", exam);
            myExam = mRealm.createObject(RealmStepExam.class, TextUtils.isEmpty(id) ? parentId : id);
        }
        checkIdsAndInsert(myCoursesID, step_id, myExam);
        myExam.setType(exam.has("type") ? JsonUtils.getString("type", exam) : "exam");
        myExam.setName(JsonUtils.getString("name", exam));
        myExam.setPassingPercentage(JsonUtils.getString("passingPercentage", exam));
        myExam.set_rev(JsonUtils.getString("_rev", exam));
        myExam.setCreatedBy(JsonUtils.getString("createdBy", exam));
        myExam.setSourcePlanet(JsonUtils.getString("sourcePlanet", exam));
        myExam.setCreatedDate(JsonUtils.getLong("createdDate", exam));
        myExam.setUpdatedDate(JsonUtils.getLong("updatedDate", exam));
        myExam.setTotalMarks(JsonUtils.getInt("totalMarks", exam));
        myExam.setNoOfQuestions(JsonUtils.getJsonArray("questions", exam).size());
        myExam.setFromNation(!TextUtils.isEmpty(parentId));
        RealmResults oldQuestions = mRealm.where(RealmExamQuestion.class).equalTo("examId",  JsonUtils.getString("_id", exam)).findAll();
        if(oldQuestions==null ||  oldQuestions.isEmpty() ){
            RealmExamQuestion.insertExamQuestions(JsonUtils.getJsonArray("questions", exam), JsonUtils.getString("_id", exam), mRealm);
        }
    }


    public String getSourcePlanet() {
        return sourcePlanet;
    }

    public void setSourcePlanet(String sourcePlanet) {
        this.sourcePlanet = sourcePlanet;
    }

    public boolean isFromNation() {
        return isFromNation;
    }

    public void setFromNation(boolean fromNation) {
        isFromNation = fromNation;
    }

    public void setNoOfQuestions(int noOfQuestions) {
        this.noOfQuestions = noOfQuestions;
    }

    public int getNoOfQuestions() {
        return noOfQuestions;
    }

    private static void checkIdsAndInsert(String myCoursesID, String step_id, RealmStepExam myExam) {
        if (!TextUtils.isEmpty(myCoursesID)) {
            myExam.setCourseId(myCoursesID);
        }
        if (!TextUtils.isEmpty(step_id)) {
            myExam.setStepId(step_id);
        }
    }


    public static int getNoOfExam(Realm mRealm, String courseId) {
        RealmResults res = mRealm.where(RealmStepExam.class).equalTo("courseId", courseId).findAll();
        if (res != null) {
            return res.size();
        }
        return 0;
    }


    public static JsonObject serializeExam(Realm mRealm, RealmStepExam exam) {
        JsonObject object = new JsonObject();
        object.addProperty("_id", exam.getId());
        object.addProperty("_rev", exam.get_rev());
        object.addProperty("name", exam.getName());
        object.addProperty("passingPercentage", exam.getPassingPercentage());
        object.addProperty("type", exam.getType());
        object.addProperty("updatedDate", exam.getUpdatedDate());
        object.addProperty("createdDate", exam.getCreatedDate());
        object.addProperty("sourcePlanet", exam.getSourcePlanet());
        object.addProperty("totalMarks", exam.getCreatedDate());
        object.addProperty("createdBy", exam.getCreatedBy());
        RealmResults<RealmExamQuestion> question = mRealm.where(RealmExamQuestion.class).equalTo("examId", exam.getId()).findAll();
        object.add("questions", RealmExamQuestion.serializeQuestions(mRealm, question));
        return object;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    public static String[] getIds(List<RealmStepExam> list) {
        String[] ids = new String[list.size()];
        int i = 0;
        for (RealmStepExam e : list
        ) {
            if (e.getType().startsWith("survey"))
                ids[i] = (e.getId());
            else
                ids[i] = e.getId() + "@" + e.getCourseId();
            i++;
        }
        Utilities.log(new Gson().toJson(ids));
        return ids;
    }

    public String getPassingPercentage() {
        return passingPercentage;
    }

    public void setPassingPercentage(String passingPercentage) {
        this.passingPercentage = passingPercentage;
    }


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(int totalMarks) {
        this.totalMarks = totalMarks;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }


    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
