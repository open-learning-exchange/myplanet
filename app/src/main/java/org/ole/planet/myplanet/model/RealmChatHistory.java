package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmChatHistory extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String user;
    private long time;
    private RealmList<Conversation> conversations;
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

//    public List<Conversation> getConversations() {
//        return conversations;
//    }
//
    public void setConversations(RealmList<Conversation> conversations) {
        this.conversations = conversations;
    }

    public RealmList<Conversation> getConversations() {
        return conversations;
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
        chatHistory.setConversations(parseConversations(mRealm, JsonUtils.getJsonArray("conversations", act)));
    }

    private static RealmList<Conversation> parseConversations(Realm realm, JsonArray jsonArray) {
        RealmList<Conversation> conversations = new RealmList<>();
        for (JsonElement element : jsonArray) {
            Conversation conversation = new Gson().fromJson(element, Conversation.class);
            Conversation realmConversation = realm.copyToRealm(conversation);
            conversations.add(realmConversation);
        }
        return conversations;
    }
}

