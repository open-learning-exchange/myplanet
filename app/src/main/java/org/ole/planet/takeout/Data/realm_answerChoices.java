package org.ole.planet.takeout.Data;

import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.realm.Realm;
import io.realm.RealmObject;

public class realm_answerChoices extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String text;
    private String questionId;

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

    public static void create(Realm mRealm, String questionId, JsonObject res, SharedPreferences settings) {
        realm_answerChoices choice = mRealm.createObject(realm_answerChoices.class, res.get("_id").getAsString());
        choice.setQuestionId(questionId);
    }
}
