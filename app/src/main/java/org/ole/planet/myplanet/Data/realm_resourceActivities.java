package org.ole.planet.myplanet.Data;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.Sort;
import io.realm.annotations.PrimaryKey;

public class realm_resourceActivities extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;


    private String createdOn;

    private String _rev;

    private long time;

    private String title;

    private String resourceId;


    private String parentCode;

    private String type;

    private String user;
    private String androidId;

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setAndroidId(String androidId) {
        this.androidId = androidId;
    }

    public String getAndroidId() {
        return androidId;
    }

    public static JsonObject serializeResourceActivities(realm_resourceActivities realm_resourceActivities) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", realm_resourceActivities.getUser());
        ob.addProperty("resourceId", realm_resourceActivities.getResourceId());
        ob.addProperty("type", realm_resourceActivities.getType());
        ob.addProperty("time", realm_resourceActivities.getTime());
        ob.addProperty("createdOn", realm_resourceActivities.getCreatedOn());
        ob.addProperty("parentCode", realm_resourceActivities.getParentCode());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        return ob;
    }

    public static void insertResourceActivities(Realm mRealm, JsonObject act) {
        realm_resourceActivities activities = mRealm.createObject(realm_resourceActivities.class, JsonUtils.getString("_id", act));
        activities.set_rev(JsonUtils.getString("_rev", act));
        activities.set_id(JsonUtils.getString("_id", act));
        activities.setType(JsonUtils.getString("type", act));
        activities.setUser(JsonUtils.getString("user", act));
        activities.setTime(JsonUtils.getLong("time", act));
        activities.setParentCode(JsonUtils.getString("parentCode", act));
        activities.setCreatedOn(JsonUtils.getString("createdOn", act));
        activities.setAndroidId(JsonUtils.getString("androidId", act));
    }
}
