package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmAnswer extends RealmObject {
    @PrimaryKey
    private String id;
    private String value;
    private RealmList<String> valueChoices;
    private int mistakes = 0;
    private boolean passed;
    private int grade;
    private String examId;
    private String questionId;
    private String submissionId;

    public static JsonArray serializeRealmAnswer(RealmList<RealmAnswer> answers) {
        Utilities.log("Ans size " + answers.size());
        JsonArray array = new JsonArray();
        for (RealmAnswer ans : answers) {
            array.add(createObject(ans));
        }
        return array;
    }

    private static JsonObject createObject(RealmAnswer ans) {
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public JsonArray getValueChoicesArray() {
        JsonArray array = new JsonArray();
        if (valueChoices == null) {
            return array;
        }
        for (String choice : valueChoices) {
            array.add(new Gson().fromJson(choice, JsonObject.class));
        }
        return array;
    }

    public RealmList<String> getValueChoices() {
        return valueChoices;
    }

    public void setValueChoices(HashMap<String, String> map, boolean isLastAnsvalid) {
        if (!isLastAnsvalid)
            this.valueChoices.clear();
        for (String key : map.keySet()) {
            JsonObject ob = new JsonObject();
            ob.addProperty("id", map.get(key));
            ob.addProperty("text", key);
            this.valueChoices.add(new Gson().toJson(ob));
        }
    }

    public void setValueChoices(RealmList<String> valueChoices) {
        this.valueChoices = valueChoices;
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
