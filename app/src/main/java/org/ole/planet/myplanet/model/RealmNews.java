package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
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
    private String message;
    private long time;
    private String createdOn;
    private String parentCode;

    public static void insertNews(String id, JsonObject doc, Realm mRealm) {
        Utilities.log("Insert my team");
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();
        if (news == null) {
            news = mRealm.createObject(RealmNews.class, id);
        }
        news.setMessage(JsonUtils.getString("message", doc));
        news.set_rev(JsonUtils.getString("_rev", doc));
        news.set_id(JsonUtils.getString("_id", doc));
        news.setTime(JsonUtils.getLong("time", doc));
        news.setCreatedOn(JsonUtils.getString("createdOn", doc));
        news.setParentCode(JsonUtils.getString("parentCode", doc));
        JsonObject user = JsonUtils.getJsonObject("user", doc);
        news.setUserId(JsonUtils.getString("_id", user));
    }

    public static RealmNews createNews(String message, Realm mRealm, RealmUserModel user){
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmNews news = mRealm.createObject(RealmNews.class, UUID.randomUUID().toString());
        news.setMessage(message);
        news.setTime(new Date().getTime());
        news.setCreatedOn(user.getPlanetCode());
        news.setParentCode(user.getParentCode());
        news.setUserId(user.getId());
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
