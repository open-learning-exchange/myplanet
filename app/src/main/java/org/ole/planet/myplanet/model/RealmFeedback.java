package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

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

    private String _rev;

    //    private RealmList<RealmMessage> messages;
    private String messages;

    private String item;
    private String parentCode;
    private String state;

    public static JsonObject serializeFeedback(RealmFeedback feedback) {
        JsonObject object = new JsonObject();
        object.addProperty("title", feedback.getTitle());
        object.addProperty("source", feedback.getSource());
        object.addProperty("status", feedback.getStatus());
        object.addProperty("priority", feedback.getPriority());
        object.addProperty("owner", feedback.getOwner());
        object.addProperty("openTime", feedback.getOpenTime());
        object.addProperty("type", feedback.getType());
        object.addProperty("url", feedback.getUrl());
        object.addProperty("parentCode", feedback.getParentCode());
        object.addProperty("state",feedback.getState());
        object.addProperty("item",feedback.getItem());
        if (feedback.get_id() != null) object.addProperty("_id", feedback.get_id());
        if (feedback.get_rev() != null) object.addProperty("_rev", feedback.get_rev());
        JsonParser parser = new JsonParser();
        try {
            object.add("messages", parser.parse(feedback.getMessages()));
        } catch (Exception err) {
            err.printStackTrace();
        }
        Utilities.log("OBJECT " +  new Gson().toJson(object));
        return object;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        RealmFeedback feedback = mRealm.where(RealmFeedback.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (feedback == null)
            feedback = mRealm.createObject(RealmFeedback.class, JsonUtils.getString("_id", act));
        feedback.set_id(JsonUtils.getString("_id", act));
        feedback.setTitle(JsonUtils.getString("title", act));
        feedback.setSource(JsonUtils.getString("source", act));
        feedback.setStatus(JsonUtils.getString("status", act));
        feedback.setPriority(JsonUtils.getString("priority", act));
        feedback.setOwner(JsonUtils.getString("owner", act));
        feedback.setOpenTime(JsonUtils.getString("openTime", act));
        feedback.setType(JsonUtils.getString("type", act));
        feedback.setUrl(JsonUtils.getString("url", act));
        feedback.setParentCode(JsonUtils.getString("parentCode",act));
        feedback.setMessages(new Gson().toJson(JsonUtils.getJsonArray("messages", act)));
        feedback.setUploaded(true);
        feedback.setItem(JsonUtils.getString("item",act));
        feedback.setState(JsonUtils.getString("state",act));
        feedback.set_rev(JsonUtils.getString("_rev", act));
    }

    public void setMessages(JsonArray messages) {
        this.messages = new Gson().toJson(messages);
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
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

    public List<FeedbackReply> getMessageList() {
        JsonParser parser = new JsonParser();
        if (TextUtils.isEmpty(messages))
            return null;
        List<FeedbackReply> feedbackReplies= new ArrayList<>();
        JsonElement e = parser.parse(messages);
        JsonArray ar = e.getAsJsonArray();
        if (ar.size() > 0) {
            for(int i = 1; i < ar.size(); i ++ ){
                JsonObject ob = ar.get(i).getAsJsonObject();
                feedbackReplies.add(new FeedbackReply(ob.get("message").getAsString(),ob.get("user").getAsString(),ob.get("time").getAsString()));
            }
        }
        return feedbackReplies;
    }

    public String getMessage() {
        JsonParser parser = new JsonParser();
        if (TextUtils.isEmpty(messages))
            return "";
        JsonElement e = parser.parse(messages);
        JsonArray ar = e.getAsJsonArray();
        if (ar.size() > 0) {
            JsonObject ob = ar.get(0).getAsJsonObject();
            return ob.get("message").getAsString();
        }
        return "";
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

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
