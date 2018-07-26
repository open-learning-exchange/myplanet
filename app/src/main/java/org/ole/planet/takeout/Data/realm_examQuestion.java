package org.ole.planet.takeout.Data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_examQuestion extends RealmObject {
    @PrimaryKey
    private String id;
    private String header;
    private String body;
    private String type;
    private String examId;
    private String correctChoice;
    private RealmList<String> choices;

    public void setCorrectChoice(String correctChoice) {
        this.correctChoice = correctChoice;
    }

    public String getCorrectChoice() {
        return correctChoice;
    }

    public String getId() {
        return id;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public void setId(String id) {
        this.id = id;
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


    public void setChoice(JsonArray array) {
        for (JsonElement s :
                array) {
            this.choices.add(s.getAsString());
        }
    }
}
