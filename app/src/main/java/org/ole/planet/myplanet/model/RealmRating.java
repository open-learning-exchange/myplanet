package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmRating extends RealmObject {

    @PrimaryKey
    private String id;
    private String createdOn;

    private String _rev;

    private long time;

    private String title;
    private String userId;

    private boolean isUpdated;

    private int rate;

    private String _id;

    private String item;

    private String comment;

    private String parentCode;
    private String planetCode;

    private String type;

    private String user;

    public static HashMap<String, JsonObject> getRatings(Realm mRealm, String type, String userId) {
        RealmResults<RealmRating> r = mRealm.where(RealmRating.class).equalTo("type", type).findAll();
        HashMap<String, JsonObject> map = new HashMap<>();
        for (RealmRating rating : r) {
            JsonObject object = getRatingsById(mRealm, rating.getType(), rating.getItem(),  userId);
            if (object != null)
                map.put(rating.getItem(), object);
        }
        return map;
    }

    public static JsonObject getRatingsById(Realm mRealm, String type, String id, String userid) {
        RealmResults<RealmRating> r = mRealm.where(RealmRating.class).equalTo("type", type).equalTo("item", id).findAll();
        if (r.size() == 0) {
            return null;
        }
        JsonObject object = new JsonObject();
        int totalRating = 0;
        for (RealmRating rating : r) {
            totalRating += rating.getRate();
        }
        RealmRating ratingObject = mRealm.where(RealmRating.class).equalTo("type", type).equalTo("userId", userid).equalTo("item", id).findFirst();
        if (ratingObject!=null){
            object.addProperty("ratingByUser", ratingObject.rate);
        }
        object.addProperty("averageRating", (float) totalRating / r.size());
        object.addProperty("total", r.size());
        return object;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isUpdated() {
        return isUpdated;
    }

    public String getPlanetCode() {
        return planetCode;
    }

    public void setPlanetCode(String planetCode) {
        this.planetCode = planetCode;
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

    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUpdated(boolean updated) {
        isUpdated = updated;
    }

    public static JsonObject serializeRating(RealmRating realm_rating) {
        JsonObject ob = new JsonObject();
        if (realm_rating.get_id()!=null)
        ob.addProperty("_id", realm_rating.get_id());
        if (realm_rating.get_rev()!=null)
            ob.addProperty("_rev", realm_rating.get_rev());
        ob.add("user", new Gson().fromJson(realm_rating.getUser(), JsonObject.class));
        ob.addProperty("item", realm_rating.getItem());
        ob.addProperty("type", realm_rating.getType());
        ob.addProperty("title", realm_rating.getTitle());
        ob.addProperty("time", realm_rating.getTime());
        ob.addProperty("comment", realm_rating.getComment());
        ob.addProperty("rate", realm_rating.getRate());
        ob.addProperty("createdOn", realm_rating.getCreatedOn());
        ob.addProperty("parentCode", realm_rating.getParentCode());
        ob.addProperty("planetCode", realm_rating.getPlanetCode());
        ob.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context));
        ob.addProperty("deviceName", NetworkUtils.getDeviceName());
        ob.addProperty("androidId", NetworkUtils.getMacAddr());
        return ob;
    }


    public static void insert(Realm mRealm, JsonObject act) {
        Utilities.log("Insert rating " + act);
        RealmRating rating = mRealm.where(RealmRating.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (rating == null)
            rating = mRealm.createObject(RealmRating.class, JsonUtils.getString("_id", act));
        rating.set_rev(JsonUtils.getString("_rev", act));
        rating.set_id(JsonUtils.getString("_id", act));
        rating.setTime(JsonUtils.getLong("time", act));
        rating.setTitle(JsonUtils.getString("title", act));
        rating.setType(JsonUtils.getString("type", act));
        rating.setItem(JsonUtils.getString("item", act));
        rating.setRate(JsonUtils.getInt("rate", act));
        rating.setUpdated(false);
        rating.setComment(JsonUtils.getString("comment", act));
        rating.setUser(new Gson().toJson(JsonUtils.getJsonObject("user", act)));
        rating.setUserId(JsonUtils.getString("_id", JsonUtils.getJsonObject("user", act)));
        rating.setParentCode(JsonUtils.getString("parentCode", act));
        rating.setParentCode(JsonUtils.getString("planetCode", act));
        rating.setCreatedOn(JsonUtils.getString("createdOn", act));
    }
}
