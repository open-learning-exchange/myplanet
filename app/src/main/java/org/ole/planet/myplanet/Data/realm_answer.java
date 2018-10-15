package org.ole.planet.myplanet.Data;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_answer extends RealmObject {
    @PrimaryKey
    private String id;
    private String value;
    private RealmList<realm_answerChoices> valueChoices;
    private int mistakes = 0;
    private boolean passed;
    private int grade;
    private String examId;
    private String questionId;
    private String submissionId;

    public String getId() {
        return id;
    }

    public static JsonArray serializeRealmAnswer(RealmList<realm_answer> answers) {
        JsonArray array = new JsonArray();
        for (realm_answer ans : answers) {
            array.add(createObject(ans));
        }
        return array;
    }

    private static JsonObject createObject(realm_answer ans) {
        JsonObject object = new JsonObject();
        if (!TextUtils.isEmpty(ans.getValue())) {
            object.addProperty("value", ans.getValue());
        } else {
            object.add("value", ans.getValueChoicesArray());
        }
        object.addProperty("mistakes", ans.getMistakes());
        object.addProperty("passed", ans.isPassed());
        return object;
    }

    public JsonArray getValueChoicesArray() {
        JsonArray array = new JsonArray();
        if (valueChoices == null) {
            return array;
        }
        for (realm_answerChoices choice : valueChoices
                ) {
            array.add(choice.serialize());
        }
        return array;
    }

    public RealmList<realm_answerChoices> getValueChoices() {
        return valueChoices;
    }

    public void setValueChoices(RealmList<realm_answerChoices> valueChoices) {
        this.valueChoices = valueChoices;
    }

    public void setValueChoices(HashMap<String, realm_answerChoices> map) {
        for (String key : map.keySet()) {
            this.valueChoices.add(map.get(key));
        }
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
