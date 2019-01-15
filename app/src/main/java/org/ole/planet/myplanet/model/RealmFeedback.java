package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmFeedback extends RealmObject {
    @PrimaryKey
    private String id;
    private String title;

    private String source;

    private String status;

    private String priority;

    private String owner;

    private String openTime;

    private String type;

    private String url;

    private boolean uploaded;

    private RealmList<RealmMessage> messages;

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
        object.add("messages", RealmMessage.serialize(feedback.messages));
        return object;
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

    public RealmList<RealmMessage> getMessages() {
        return messages;
    }

    public void setMessages(RealmList<RealmMessage> messages) {
        this.messages = messages;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }


}
