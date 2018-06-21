package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_offlineActivities extends RealmObject {
    @PrimaryKey
    private String id;
    private String userFullName;
    private String userId;
    private String type;
    private String description;
    private Integer loginTime;
    private Integer logoutTime;

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

    public Integer getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Integer loginTime) {
        this.loginTime = loginTime;
    }

    public Integer getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(Integer logoutTime) {
        this.logoutTime = logoutTime;
    }
}
