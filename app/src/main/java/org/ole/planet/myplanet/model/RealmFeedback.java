package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.utilities.JsonUtils;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmFeedback extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String title;

    private String source;

    private String status;

    private String priority;

    private String owner;

    private String openTime;

    private String type;

    private String url;

    private boolean uploaded;

//    private RealmList<RealmMessage> messages;
    private String messages;

    public static JsonObject serializeFeedback( RealmFeedback feedback) {
        JsonObject object = new JsonObject();
        object.addProperty("title", feedback.getTitle());
        object.addProperty("source", feedback.getSource());
        object.addProperty("status", feedback.getStatus());
        object.addProperty("priority", feedback.getPriority());
        object.addProperty("owner", feedback.getOwner());
        object.addProperty("openTime", feedback.getOpenTime());
        object.addProperty("type", feedback.getType());
        object.addProperty("url", feedback.getUrl());
//        object.add("messages", RealmMessage.serialize(feedback.messages));
        JsonParser parser = new JsonParser();
        object.add("messages", parser.parse(feedback.messages));
        return object;
    }


    public static void insertFeedback(Realm mRealm, JsonObject act) {
        RealmFeedback feedback = mRealm.where(RealmFeedback.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (feedback == null)
            feedback = mRealm.createObject(RealmFeedback.class, JsonUtils.getString("_id", act));
        feedback.set_id(JsonUtils.getString("_", act));
        feedback.setTitle(JsonUtils.getString("title", act));
        feedback.setSource(JsonUtils.getString("source", act));
        feedback.setStatus(JsonUtils.getString("status", act));
        feedback.setPriority(JsonUtils.getString("priority", act));
        feedback.setOwner(JsonUtils.getString("owner", act));
        feedback.setOpenTime(JsonUtils.getString("openTime", act));
        feedback.setType(JsonUtils.getString("type", act));
        feedback.setUrl(JsonUtils.getString("url", act));
        feedback.setMessages(new Gson().toJson(JsonUtils.getJsonArray("messages", act)));
        feedback.setUploaded(true);
    }

    public void setMessages(JsonArray messages){
        for (JsonElement e: messages
             ) {

        }
    }
    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

//    public RealmList<RealmMessage> getMessages() {
//        return messages;
//    }
//
//    public void setMessages(RealmList<RealmMessage> messages) {
//        this.messages = messages;
//    }


    public String getMessages() {
        return messages;
    }

    public void setMessages(String messages) {
        this.messages = messages;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }


}
