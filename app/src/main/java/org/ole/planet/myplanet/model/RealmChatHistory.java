package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmChatHistory extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String user;
    private long time;
    private String conversations;
    private boolean uploaded;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getConversations() {
        return conversations;
    }

    public void setConversations(String conversations) {
        this.conversations = new Gson().toJson(conversations);
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        Utilities.log("Insert chatHistory " + act);
        RealmChatHistory chatHistory = mRealm.where(RealmChatHistory.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (chatHistory == null)
            chatHistory = mRealm.createObject(RealmChatHistory.class, JsonUtils.getString("_id", act));
        chatHistory.set_rev(JsonUtils.getString("_rev", act));
        chatHistory.set_id(JsonUtils.getString("_id", act));
        chatHistory.setTime(JsonUtils.getLong("time", act));
        chatHistory.setUser(new Gson().toJson(JsonUtils.getJsonObject("user", act)));
        chatHistory.setConversations(new Gson().toJson(JsonUtils.getJsonArray("conversations", act)));
    }
}
