package org.ole.planet.takeout.Data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_answer extends RealmObject {
    @PrimaryKey
    private String id;
    private String value;
    private int mistakes = 0;
    private boolean passed;
    private int grade;
    private String examId;
    private String questionId;
    private String submissionId;

    public String getId() {
        return id;
    }

    public static JsonArray serializeRealmAnswer(Realm mRealm, RealmList<realm_answer> answers) {
        JsonArray array = new JsonArray();
        for (realm_answer ans : answers) {
            JsonObject object = new JsonObject();
            object.addProperty("value", ans.getValue());
            object.addProperty("mistakes", ans.getMistakes());
            object.addProperty("passed", ans.isPassed());
            array.add(object);
        }

        return array;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }


    public void setValue(String value) {
        this.value = value;
    }

    public int getMistakes() {
        return mistakes;
    }

    public void setMistakes(int mistakes) {
        this.mistakes = mistakes;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }
}
