package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmNews extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String userId;
    private String user;
    private String message;
    private String docType;
    private String viewableBy;
    private String viewableId;
    private String avatar;
    private String replyTo;
    private String userName;
    private long time;
    private String createdOn;
    private String parentCode;

    public static void insert(Realm mRealm, JsonObject doc) {
        Utilities.log("Insert news " + doc);
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", JsonUtils.getString("_id", doc)).findFirst();
        if (news == null) {
            news = mRealm.createObject(RealmNews.class, JsonUtils.getString("_id", doc));
        }
        news.setMessage(JsonUtils.getString("message", doc));
        news.set_rev(JsonUtils.getString("_rev", doc));
        news.set_id(JsonUtils.getString("_id", doc));
        news.setTime(JsonUtils.getLong("time", doc));
        news.setViewableBy(JsonUtils.getString("viewableBy", doc));
        news.setDocType(JsonUtils.getString("docType", doc));
        news.setAvatar(JsonUtils.getString("avatar", doc));
        news.setViewableId(JsonUtils.getString("viewableId", doc));
        news.setCreatedOn(JsonUtils.getString("createdOn", doc));
        news.setReplyTo(JsonUtils.getString("replyTo", doc));
        Utilities.log("reply to " + JsonUtils.getString("replyTo", doc));
        news.setParentCode(JsonUtils.getString("parentCode", doc));
        JsonObject user = JsonUtils.getJsonObject("user", doc);
        news.setUser(new Gson().toJson(JsonUtils.getJsonObject("user", doc)));
        news.setUserId(JsonUtils.getString("_id", user));
        news.setUserName(JsonUtils.getString("name", user));
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getViewableBy() {
        return viewableBy;
    }

    public void setViewableBy(String viewableBy) {
        this.viewableBy = viewableBy;
    }

    public String getViewableId() {
        return viewableId;
    }

    public void setViewableId(String viewableId) {
        this.viewableId = viewableId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    public static JsonObject serializeNews(RealmNews news, RealmUserModel user) {
        JsonObject object = new JsonObject();
        object.addProperty("message", news.getMessage());
        if (news.get_id() != null)
            object.addProperty("_id", news.get_id());
        if (news.get_rev() != null)
            object.addProperty("_rev", news.get_rev());
        object.addProperty("time", news.getTime());
        object.addProperty("createdOn", news.getCreatedOn());
        object.addProperty("docType", news.getDocType());
        object.addProperty("viewableId", news.getViewableId());
        object.addProperty("viewableBy", news.getViewableBy());
        object.addProperty("avatar", news.getAvatar());
        object.addProperty("createdOn", news.getCreatedOn());
        object.addProperty("replyTo", news.getReplyTo());
        object.addProperty("parentCode", news.getParentCode());
        object.add("user", new Gson().fromJson(news.getUser(), JsonObject.class));
        //  object.add("user", user.serialize());
        return object;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public static RealmNews createNews(HashMap<String, String> map, Realm mRealm, RealmUserModel user) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmNews news = mRealm.createObject(RealmNews.class, UUID.randomUUID().toString());
        news.setMessage(map.get("message"));
        news.setTime(new Date().getTime());
        news.setCreatedOn(user.getPlanetCode());
        news.setViewableId(map.get("viewableId"));
        news.setAvatar("");
        news.setDocType("message");
        news.setViewableBy(map.get("viewableBy"));
        news.setUserName(user.getName());
        news.setParentCode(user.getParentCode());
        news.setUserId(user.getId());
        news.setReplyTo(map.containsKey("replyTo") ? map.get("replyTo") : "");
        news.setUser(new Gson().toJson(user.serialize()));
        mRealm.commitTransaction();
        return news;
    }

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }
}
