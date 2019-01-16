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
    private String name;
    private String type;
    private String stepId;
    private String courseId;
    private String passingPercentage;
    private String totalMarks;


    public static void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam, Realm mRealm) {
        RealmStepExam myExam = mRealm.where(RealmStepExam.class).equalTo("id", exam.get("_id").getAsString()).findFirst();
        if (myExam == null) {
            myExam = mRealm.createObject(RealmStepExam.class, exam.get("_id").getAsString());
        }
        checkIdsAndInsert(myCoursesID, step_id, myExam);
        myExam.setType(exam.has("type") ? JsonUtils.getString("type", exam) : "exam");
        myExam.setName(JsonUtils.getString("name", exam));
        myExam.setPassingPercentage(JsonUtils.getString("passingPercentage", exam));
        myExam.setTotalMarks(JsonUtils.getString("totalMarks", exam));
        RealmExamQuestion.insertExamQuestions(JsonUtils.getJsonArray("questions", exam), JsonUtils.getString("_id", exam), mRealm);
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
        object.addProperty("name", exam.getName());
        object.addProperty("passingPercentage", exam.getPassingPercentage());
        object.addProperty("type", exam.getType());
        RealmResults<RealmExamQuestion> question = mRealm.where(RealmExamQuestion.class).equalTo("examId", exam.getId()).findAll();
        object.add("questions", RealmExamQuestion.serializeQuestions(mRealm, question));
        return object;
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

    public String getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(String totalMarks) {
        this.totalMarks = totalMarks;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
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
