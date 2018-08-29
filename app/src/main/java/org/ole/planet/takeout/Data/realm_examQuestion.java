package org.ole.planet.takeout.Data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class realm_examQuestion extends RealmObject {
    @PrimaryKey
    private String id;
    private String header;
    private String body;
    private String type;
    private String examId;
    private String correctChoice;
    private String marks;
    private RealmList<String> choices;

    public static void insertExamQuestions(JsonArray questions, String examId, Realm mRealm) {
        for (int i = 0; i < questions.size(); i++) {
            String questionId = UUID.randomUUID().toString();
            JsonObject question = questions.get(i).getAsJsonObject();
            realm_examQuestion myQuestion = mRealm.createObject(realm_examQuestion.class, questionId);
            myQuestion.setExamId(examId);
            myQuestion.setBody(question.get("body").getAsString());
            myQuestion.setType(question.get("type").getAsString());
            myQuestion.setHeader(question.get("header").getAsString());
            if (question.has("marks"))
                myQuestion.setMarks(question.get("marks").getAsString());
            insertChoices(question, questionId, mRealm, myQuestion);
        }
    }

    private static void insertChoices(JsonObject question, String questionId, Realm mRealm, realm_examQuestion myQuestion) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isMultipleChoice = question.has("correctChoice") && question.get("type").getAsString().equals("select");
        if (isMultipleChoice) {
            JsonArray array = question.get("choices").getAsJsonArray();
            realm_answerChoices.insertChoices(questionId, array, mRealm, settings);
            insertCorrectChoice(array, question, myQuestion);
        } else {
            myQuestion.setChoice(question.get("choices").getAsJsonArray(), myQuestion);
        }
    }

    private static void insertCorrectChoice(JsonArray array, JsonObject question, realm_examQuestion myQuestion) {
        for (int a = 0; a < array.size(); a++) {
            JsonObject res = array.get(a).getAsJsonObject();
            if (question.get("correctChoice").getAsString().equals(res.get("id").getAsString()))
                myQuestion.setCorrectChoice(res.get("text").getAsString());
        }
    }


    public String getMarks() {
        return marks;
    }

    public void setMarks(String marks) {
        this.marks = marks;
    }

    public String getCorrectChoice() {
        return correctChoice;
    }

    public void setCorrectChoice(String correctChoice) {
        this.correctChoice = correctChoice;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public RealmList<String> getChoices() {
        return choices;
    }

    public void setChoices(RealmList<String> choices) {
        this.choices = choices;
    }

    public void setChoice(JsonArray array, realm_examQuestion myQuestion) {
        for (JsonElement s :
                array) {
            myQuestion.choices.add(s.getAsString());
        }
    }

    public JsonArray getChoicesArrayObj(Realm mRealm) {
        JsonArray array = new JsonArray();
        RealmResults<realm_answerChoices> choices = mRealm.where(realm_answerChoices.class).equalTo("questionId", getId()).findAll();
        for (realm_answerChoices s : choices) {
            array.add(s.serialize());
        }
        return array;
    }

    public JsonArray getChoicesArray() {
        JsonArray array = new JsonArray();
        for (String s : getChoices()) {
            array.add(s);
        }
        return array;
    }

    public static JsonArray serializeQuestions(Realm mRealm, RealmResults<realm_examQuestion> question) {
        JsonArray array = new JsonArray();
        if (question != null) {
            for (realm_examQuestion que : question) {
                JsonObject object = new JsonObject();
                object.addProperty("header", que.getHeader());
                object.addProperty("body", que.getBody());
                object.addProperty("type", que.getType());
                object.addProperty("marks", que.getMarks());
                object.addProperty("correctChoice", que.getCorrectChoice());
                if (!TextUtils.isEmpty(que.getCorrectChoice())) {
                    object.add("choices", que.getChoicesArrayObj(mRealm));
                } else {
                    object.add("choices", que.getChoicesArray());
                }
                array.add(object);
            }
        }
        return array;
    }


}
