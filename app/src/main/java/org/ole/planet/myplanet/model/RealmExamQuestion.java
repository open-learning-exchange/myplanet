package org.ole.planet.myplanet.model;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonParserUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmExamQuestion extends RealmObject {
    @PrimaryKey
    private String id;
    private String header;
    private String body;
    private String type;
    private String examId;
    private RealmList<String> correctChoice;
    private String marks;
    private String choices;

    public static void insertExamQuestions(JsonArray questions, String examId, Realm mRealm) {
        for (int i = 0; i < questions.size(); i++) {
            JsonObject question = questions.get(i).getAsJsonObject();
            String questionId = Base64.encodeToString(question.toString().getBytes(), Base64.NO_WRAP);
            RealmExamQuestion myQuestion = mRealm.where(RealmExamQuestion.class).equalTo("id", questionId).findFirst();
            if (myQuestion == null) {
                myQuestion = mRealm.createObject(RealmExamQuestion.class, questionId);
            }
            myQuestion.setExamId(examId);
            myQuestion.setBody(JsonUtils.getString("body", question));
            myQuestion.setType(JsonUtils.getString("type", question));
            myQuestion.setHeader(JsonUtils.getString("title", question));
            myQuestion.setMarks(JsonUtils.getString("marks", question));
            myQuestion.setChoices(new Gson().toJson(JsonUtils.getJsonArray("choices", question)));
            boolean isMultipleChoice = question.has("correctChoice") && JsonUtils.getString("type", question).startsWith("select");
            if (isMultipleChoice)
                insertCorrectChoice(question.get("choices").getAsJsonArray(), question, myQuestion);
        }
    }

    private static void insertCorrectChoice(JsonArray array, JsonObject question, RealmExamQuestion myQuestion) {
        for (int a = 0; a < array.size(); a++) {
            JsonObject res = array.get(a).getAsJsonObject();
            if (question.get("correctChoice").isJsonArray()) {
                myQuestion.correctChoice = new RealmList<>();
                myQuestion.setCorrectChoiceArray(JsonUtils.getJsonArray("correctChoice", question), myQuestion);
            } else if (JsonUtils.getString("correctChoice", question).equals(JsonUtils.getString("id", res))) {
                myQuestion.correctChoice = new RealmList<>();
                myQuestion.correctChoice.add(JsonUtils.getString("res", res));
            }
        }
    }

    private void setCorrectChoiceArray(JsonArray array, RealmExamQuestion question) {
        for (int i = 0; i < array.size(); i++) {
            question.correctChoice.add(JsonUtils.getString(array, i).toLowerCase());
        }
    }

    public static JsonArray serializeQuestions(Realm mRealm, RealmResults<RealmExamQuestion> question) {
        JsonArray array = new JsonArray();
        for (RealmExamQuestion que : question) {
            JsonObject object = new JsonObject();
            object.addProperty("header", que.getHeader());
            object.addProperty("body", que.getBody());
            object.addProperty("type", que.getType());
            object.addProperty("marks", que.getMarks());
            object.add("choices", JsonParserUtils.getStringAsJsonArray(que.getChoices()));
            object.add("correctChoice", que.getCorrectChoiceArray());
            array.add(object);
        }
        return array;
    }

    public String getMarks() {
        return marks;
    }

    public void setMarks(String marks) {
        this.marks = marks;
    }

    public RealmList<String> getCorrectChoice() {
        return correctChoice;
    }

    public JsonArray getCorrectChoiceArray() {
        JsonArray array = new JsonArray();
        for (String s : correctChoice) {
            array.add(s);
        }
        return array;
    }

    public void setCorrectChoice(RealmList<String> correctChoice) {
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
//
//    public JsonArray getChoicesArrayObj(Realm mRealm) {
//        JsonArray array = new JsonArray();
//        RealmResults<realm_answerChoices> choices = mRealm.where(realm_answerChoices.class).equalTo("questionId", getId()).findAll();
//        for (realm_answerChoices s : choices) {
//            array.add(s.serialize());
//        }
//        return array;
//    }
//
//    public JsonArray getChoicesArray() {
//        JsonArray array = new JsonArray();
//        for (String s : getChoices()) {
//            array.add(s);
//        }
//        return array;
//    }

    public void setType(String type) {
        this.type = type;
    }

    public String getChoices() {
        return choices;
    }

    public void setChoices(String choices) {
        this.choices = choices;
    }
}
