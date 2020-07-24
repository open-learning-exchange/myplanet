package org.ole.planet.myplanet.model;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmTeamLog extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String teamId;
    private String user;
    private String type;
    private String teamType;
    private String createdOn;
    private String parentCode;
    private Long time;
    private boolean uploaded = false;

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getTeamId() {
        return teamId;
    }

    public static long getVisitCount(Realm realm, String userName, String teamId) {
        return realm.where(RealmTeamLog.class).equalTo("type", "teamVisit").equalTo("user", userName).equalTo("teamId", teamId).count();
    }

    public static long getVisitByTeam(Realm realm, String teamId) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -30);
        return realm.where(RealmTeamLog.class).equalTo("type", "teamVisit").equalTo("teamId", teamId).greaterThan("time", calendar.getTimeInMillis()).count();
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getTeamType() {
        return teamType;
    }

    public void setTeamType(String teamType) {
        this.teamType = teamType;
    }

    public static JsonObject serializeTeamActivities(RealmTeamLog log, Context context) {
        JsonObject ob = new JsonObject();
        ob.addProperty("user", log.getUser());
        ob.addProperty("type", log.getType());
        ob.addProperty("createdOn", log.getCreatedOn());
        ob.addProperty("parentCode", log.getParentCode());
        ob.addProperty("teamType", log.getTeamType());
        ob.addProperty("time", log.getTime());
        ob.addProperty("teamId", log.getTeamId());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        ob.addProperty("deviceName", NetworkUtils.getDeviceName());
        ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        if (!TextUtils.isEmpty(log.get_rev())) {
            ob.addProperty("_rev", log.get_rev());
            ob.addProperty("_id", log.get_id());
        }
        return ob;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        Utilities.log("Insert team visits");
        RealmTeamLog tag = mRealm.where(RealmTeamLog.class).equalTo("id", JsonUtils.getString("_id", act)).findFirst();
        if (tag == null)
            tag = mRealm.createObject(RealmTeamLog.class, JsonUtils.getString("_id", act));
        tag.set_rev(JsonUtils.getString("_rev", act));
        tag.set_id(JsonUtils.getString("_id", act));
        tag.setType(JsonUtils.getString("type", act));
        tag.setUser(JsonUtils.getString("user", act));
        tag.setCreatedOn(JsonUtils.getString("createdOn", act));
        tag.setParentCode(JsonUtils.getString("parentCode", act));
        tag.setTime(JsonUtils.getLong("time", act));
        tag.setTeamId(JsonUtils.getString("teamId", act));
        tag.setTeamType(JsonUtils.getString("teamType", act));
    }


}

