package org.ole.planet.myplanet.model;

import android.content.Context;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.Sort;
import io.realm.annotations.PrimaryKey;

public class RealmOfflineActivity extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String userName;
    private String userId;
    private String type;
    private String description;
    private String createdOn;
    private String parentCode;
    private Long loginTime;
    private Long logoutTime;
    private String androidId;

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
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


    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Long loginTime) {
        this.loginTime = loginTime;
    }

    public Long getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(Long logoutTime) {
        this.logoutTime = logoutTime;
    }

    public String getAndroidId() {
        return androidId;
    }

    public void setAndroidId(String androidId) {
        this.androidId = androidId;
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

    public static JsonObject serializeLoginActivities(RealmOfflineActivity realm_offlineActivities, Context context) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", realm_offlineActivities.getUserName());
        ob.addProperty("type", realm_offlineActivities.getType());
        ob.addProperty("loginTime", realm_offlineActivities.getLoginTime());
        ob.addProperty("logoutTime", realm_offlineActivities.getLogoutTime());
        ob.addProperty("createdOn", realm_offlineActivities.getCreatedOn());
        ob.addProperty("parentCode", realm_offlineActivities.getParentCode());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        ob.addProperty("deviceName", NetworkUtils.getDeviceName());
        ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        if (realm_offlineActivities.get_id() != null) {
            ob.addProperty("_id", realm_offlineActivities.getLogoutTime());
        }
        if (realm_offlineActivities.get_rev() != null) {
            ob.addProperty("_rev", realm_offlineActivities.get_rev());
        }
        return ob;
    }

    public static RealmOfflineActivity getRecentLogin(Realm mRealm) {
        RealmOfflineActivity s = mRealm.where(RealmOfflineActivity.class).equalTo("type", UserProfileDbHandler.KEY_LOGIN).sort("loginTime", Sort.DESCENDING).findFirst();
        return s;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        RealmOfflineActivity activities = mRealm.where(RealmOfflineActivity.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (activities == null)
            activities = mRealm.createObject(RealmOfflineActivity.class, JsonUtils.getString("_id", act));
        activities.set_rev(JsonUtils.getString("_rev", act));
        activities.set_id(JsonUtils.getString("_id", act));
        activities.setLoginTime(JsonUtils.getLong("loginTime", act));
        activities.setType(JsonUtils.getString("type", act));
        activities.setUserName(JsonUtils.getString("user", act));
        activities.setParentCode(JsonUtils.getString("parentCode", act));
        activities.setCreatedOn(JsonUtils.getString("createdOn", act));
        activities.setUserName(JsonUtils.getString("user", act));
        activities.setLogoutTime(JsonUtils.getLong("logoutTime", act));
        activities.setAndroidId(JsonUtils.getString("androidId", act));
    }

    public void changeRev(JsonObject r) {
        if (r != null) {
            this.set_rev(JsonUtils.getString("_rev", r));
            this.set_id(JsonUtils.getString("_id", r));
        }
    }
}
