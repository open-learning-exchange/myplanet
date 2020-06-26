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

public class RealmCourseActivity extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;

    private String createdOn;

    private String _rev;

    private long time;

    private String title;

    private String courseId;


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

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
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


    public static void createActivity(Realm realm, RealmUserModel userModel, RealmMyCourse course) {
        if (!realm.isInTransaction())
            realm.beginTransaction();
        RealmCourseActivity activity = realm.createObject(RealmCourseActivity.class, UUID.randomUUID().toString());
        activity.setType("visit");
        activity.setTitle(course.getCourseTitle());
        activity.setCourseId(course.getCourseId());
        activity.setTime(new Date().getTime());
        activity.setParentCode(userModel.getParentCode());
        activity.setCreatedOn(userModel.getPlanetCode());
        activity.setCreatedOn(userModel.getPlanetCode());
        activity.setUser(userModel.getName());
        realm.commitTransaction();
    }

    public static JsonObject serializeSerialize(RealmCourseActivity realm_courseActivities) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", realm_courseActivities.getUser());
        ob.addProperty("courseId", realm_courseActivities.getCourseId());
        ob.addProperty("type", realm_courseActivities.getType());
        ob.addProperty("title", realm_courseActivities.getTitle());
        ob.addProperty("time", realm_courseActivities.getTime());
        ob.addProperty("createdOn", realm_courseActivities.getCreatedOn());
        ob.addProperty("parentCode", realm_courseActivities.getParentCode());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        ob.addProperty("deviceName", NetworkUtils.getDeviceName());
        return ob;
    }


}
