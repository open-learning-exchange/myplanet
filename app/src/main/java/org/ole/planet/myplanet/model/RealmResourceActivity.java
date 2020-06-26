package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmResourceActivity extends RealmObject {
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


    public static JsonObject serializeResourceActivities(RealmResourceActivity realm_resourceActivities) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", realm_resourceActivities.getUser());
        ob.addProperty("resourceId", realm_resourceActivities.getResourceId());
        ob.addProperty("type", realm_resourceActivities.getType());
        ob.addProperty("title", realm_resourceActivities.getTitle());
        ob.addProperty("time", realm_resourceActivities.getTime());
        ob.addProperty("createdOn", realm_resourceActivities.getCreatedOn());
        ob.addProperty("parentCode", realm_resourceActivities.getParentCode());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        ob.addProperty("deviceName", NetworkUtils.getDeviceName());
        return ob;
    }


    public static void onSynced(Realm mRealm, SharedPreferences settings) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", ""))
                .findFirst();
        if (user == null) {
            Utilities.log("User is null");
            return;
        }
        if (user.getId().startsWith("guest")){
            return;
        }
        RealmResourceActivity activities = mRealm.createObject(RealmResourceActivity.class, UUID.randomUUID().toString());
        activities.setUser(user.getName());
        activities.set_rev(null);
        activities.set_id(null);
        activities.setParentCode(user.getParentCode());
        activities.setCreatedOn(user.getPlanetCode());
        activities.setType("sync");
        activities.setTime(new Date().getTime());
        Utilities.log("Saved Sync Activity");

        mRealm.commitTransaction();
    }

}
