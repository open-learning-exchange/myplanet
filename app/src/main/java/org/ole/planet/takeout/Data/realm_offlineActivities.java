package org.ole.planet.takeout.Data;

import com.google.gson.JsonObject;

import org.jboss.security.auth.spi.Users;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.TimeUtils;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.Sort;
import io.realm.annotations.PrimaryKey;

public class realm_offlineActivities extends RealmObject {
    @PrimaryKey
    private String id;
    private String userFullName;
    private String userId;
    private String type;
    private String description;
    private Long loginTime;
    private Long logoutTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userName) {
        this.userFullName = userName;
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
        ob.addProperty("user", realm_offlineActivities.getUserFullName());
        ob.addProperty("type", realm_offlineActivities.getType());
        ob.addProperty("loginTime", realm_offlineActivities.getLoginTime());
        ob.addProperty("logoutTime", realm_offlineActivities.getLogoutTime());
        return ob;
    }

    public static realm_offlineActivities getRecentLogin(Realm mRealm) {
        realm_offlineActivities s = mRealm.where(realm_offlineActivities.class).equalTo("type", UserProfileDbHandler.KEY_LOGIN).sort("loginTime", Sort.DESCENDING).findFirst();
        return s;

    }
}
