package org.ole.planet.myplanet.Data;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class realm_stepExam extends RealmObject {
    @PrimaryKey
    private String id;
    private String name;
    private String type;
    private String stepId;
    private String courseId;
    private String passingPercentage;
    private String totalMarks;


    public static void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam, Realm mRealm) {
        realm_stepExam myExam = mRealm.where(realm_stepExam.class).equalTo("id", exam.get("_id").getAsString()).findFirst();
        if (myExam == null) {
            myExam = mRealm.createObject(realm_stepExam.class, exam.get("_id").getAsString());
        }
        checkIdsAndInsert(myCoursesID, step_id, myExam);
        myExam.setType(exam.has("type") ? JsonUtils.getString("type", exam) : "exam");
        myExam.setName(JsonUtils.getString("name", exam));
        myExam.setPassingPercentage(JsonUtils.getString("passingPercentage", exam));
        myExam.setTotalMarks(JsonUtils.getString("totalMarks", exam));
        realm_examQuestion.insertExamQuestions(JsonUtils.getJsonArray("questions", exam), JsonUtils.getString("_id", exam), mRealm);
    }

    private static void checkIdsAndInsert(String myCoursesID, String step_id, realm_stepExam myExam) {
        if (!TextUtils.isEmpty(myCoursesID)) {
            myExam.setCourseId(myCoursesID);
        }
        if (!TextUtils.isEmpty(step_id)) {
            myExam.setStepId(step_id);
        }
    }


    public static int getNoOfExam(Realm mRealm, String courseId) {
        RealmResults res = mRealm.where(realm_stepExam.class).equalTo("courseId", courseId).findAll();
        if (res != null) {
            return res.size();
        }
        return 0;
    }

    public static JsonObject serializeExam(Realm mRealm, realm_stepExam exam) {
        JsonObject object = new JsonObject();
        object.addProperty("name", exam.getName());
        object.addProperty("passingPercentage", exam.getPassingPercentage());
        object.addProperty("type", exam.getType());
        RealmResults<realm_examQuestion> question = mRealm.where(realm_examQuestion.class).equalTo("examId", exam.getId()).findAll();
        object.add("questions", realm_examQuestion.serializeQuestions(mRealm, question));
        return object;
    }

    public static String[] getIds(List<realm_stepExam> list) {
        String[] ids = new String[list.size()];
        int i = 0;
        for (realm_stepExam e : list
                ) {
            if (e.getType().equals("survey"))
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
