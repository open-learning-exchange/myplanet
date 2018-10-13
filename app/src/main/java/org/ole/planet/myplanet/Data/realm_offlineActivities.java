package org.ole.planet.myplanet.Data;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.jboss.security.auth.spi.Users;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.Sort;
import io.realm.annotations.PrimaryKey;

public class realm_offlineActivities extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String userName;
    private String userId;
    private String type;
    private String description;
    private Long loginTime;
    private Long logoutTime;

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


    public static JsonObject serializeLoginActivities(realm_offlineActivities realm_offlineActivities) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", realm_offlineActivities.getUserName());
        ob.addProperty("type", realm_offlineActivities.getType());
        ob.addProperty("loginTime", realm_offlineActivities.getLoginTime());
        ob.addProperty("logoutTime", realm_offlineActivities.getLogoutTime());
        if (realm_offlineActivities.get_id() != null) {
            ob.addProperty("_id", realm_offlineActivities.getLogoutTime());
        }
        if (realm_offlineActivities.get_rev() != null) {
            ob.addProperty("_rev", realm_offlineActivities.get_rev());
        }
        return ob;
    }

    public static realm_offlineActivities getRecentLogin(Realm mRealm) {
        realm_offlineActivities s = mRealm.where(realm_offlineActivities.class).equalTo("type", UserProfileDbHandler.KEY_LOGIN).sort("loginTime", Sort.DESCENDING).findFirst();
        return s;
    }

    public static void insertOfflineActivities(Realm mRealm, JsonObject act) {
        realm_offlineActivities activities = mRealm.createObject(realm_offlineActivities.class, act.get("_id").toString());
        activities.set_rev(act.get("_rev").toString());
        activities.set_id(act.get("_id").toString());
        activities.setLoginTime(act.get("loginTime") instanceof JsonNull ? act.get("logoutTime").getAsLong() : 0L);
        activities.setType(act.get("type").getAsString());
        activities.setUserName(act.get("user").getAsString());
        activities.setLogoutTime(act.has("logoutTime") ? act.get("logoutTime").getAsLong() : 0L);
    }
}
