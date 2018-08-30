package org.ole.planet.takeout.Data;

import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.takeout.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmObject;

public class realm_answerChoices extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String text;
    private String questionId;

    public static void create(Realm mRealm, String questionId, JsonObject res, SharedPreferences settings) {
        realm_answerChoices choice = mRealm.where(realm_answerChoices.class).equalTo("id", res.get("id").getAsString()).findFirst();
        if (choice == null) {
            choice = mRealm.createObject(realm_answerChoices.class, res.get("id").getAsString());
        }
        choice.setText(res.get("text").getAsString());
        choice.setQuestionId(questionId);
    }

    public static void insertChoices(String questionId, JsonArray choices, Realm mRealm, SharedPreferences settings) {
        for (int i = 0; i < choices.size(); i++) {
            JsonObject res = choices.get(i).getAsJsonObject();
            realm_answerChoices.create(mRealm, questionId, res, settings);
            Utilities.log("Insert choices");
        }
    }


    public JsonObject serialize() {
        JsonObject object = new JsonObject();
        object.addProperty("id", getId());
        object.addProperty("text", getText());
        return object;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }
}
