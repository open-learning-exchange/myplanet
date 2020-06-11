package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyHealthPojo extends RealmObject {
    @PrimaryKey
    private String _id;
    private String userId;
    private boolean isUpdated;
    private String _rev;
    private String data;
    private float temperature;
    private int pulse;
    private String bp;
    private float height;
    private float weight;
    private String vision;
    private long date;
    private String hearing;
    private String conditions;
    private boolean selfExamination;
    private String planetCode;
    private boolean hasInfo;
    private String profileId;
    private String creatorId;
    private String gender;
    private int age;

    public static void insert(Realm mRealm, JsonObject act) {
        Utilities.log("Insert health " + new Gson().toJson(act));
        RealmMyHealthPojo myHealth = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (myHealth == null)
            myHealth = mRealm.createObject(RealmMyHealthPojo.class, JsonUtils.getString("_id", act));
        myHealth.setData(JsonUtils.getString("data", act));
        myHealth.setUserId(JsonUtils.getString("_id", act));
        myHealth.set_rev(JsonUtils.getString("_rev", act));
        myHealth.setTemperature(JsonUtils.getFloat("temperature", act));
        myHealth.setIsUpdated(false);
        myHealth.setPulse(JsonUtils.getInt("pulse", act));
        myHealth.setHeight(JsonUtils.getFloat("height", act));
        myHealth.setWeight(JsonUtils.getFloat("weight", act));
        myHealth.setVision(JsonUtils.getString("vision", act));
        myHealth.setHearing(JsonUtils.getString("hearing", act));
        myHealth.setBp(JsonUtils.getString("bp", act));
        myHealth.setSelfExamination(JsonUtils.getBoolean("selfExamination", act));
        myHealth.setHasInfo(JsonUtils.getBoolean("hasInfo", act));
        myHealth.setDate(JsonUtils.getLong("date", act));
        myHealth.setProfileId(JsonUtils.getString("profileId", act));
        myHealth.setCreatorId(JsonUtils.getString("creatorId", act));
        myHealth.setAge(JsonUtils.getInt("age", act));
        myHealth.setGender(JsonUtils.getString("gender", act));
        myHealth.setPlanetCode(JsonUtils.getString("planetCode", act));
        myHealth.setConditions(new Gson().toJson(JsonUtils.getJsonObject("conditions", act)));
    }

    public JsonObject getEncryptedDataAsJson(RealmUserModel model) {
        if (!TextUtils.isEmpty(this.data))
            return new Gson().fromJson(AndroidDecrypter.decrypt(this.data, model.getKey(), model.getIv()), JsonObject.class);
        return new JsonObject();
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public boolean getIsUpdated() {
        return isUpdated;
    }

    public void setIsUpdated(boolean isUpdated) {
        this.isUpdated = isUpdated;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }


    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getPulse() {
        return pulse;
    }

    public void setPulse(int pulse) {
        this.pulse = pulse;
    }

    public String getBp() {
        return bp;
    }

    public void setBp(String bp) {
        this.bp = bp;
    }


    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getVision() {
        return vision;
    }

    public void setVision(String vision) {
        this.vision = vision;
    }

    public String getHearing() {
        return hearing;
    }

    public void setHearing(String hearing) {
        this.hearing = hearing;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public boolean isSelfExamination() {
        return selfExamination;
    }

    public void setSelfExamination(boolean selfExamination) {
        this.selfExamination = selfExamination;
    }

    public String getPlanetCode() {
        return planetCode;
    }

    public void setPlanetCode(String planetCode) {
        this.planetCode = planetCode;
    }

    public boolean isHasInfo() {
        return hasInfo;
    }

    public void setHasInfo(boolean hasInfo) {
        this.hasInfo = hasInfo;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }


    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public static JsonObject serialize(RealmMyHealthPojo health) {
        JsonObject object = new JsonObject();
        if (!TextUtils.isEmpty(health.getUserId()))
            object.addProperty("_id", health.getUserId());
        if (!TextUtils.isEmpty(health.get_rev()))
            object.addProperty("_rev", health.get_rev());
        object.addProperty("data", health.getData());

        JsonUtils.addFloat(object, "temperature", health.getTemperature());
        JsonUtils.addInteger(object, "pulse", health.getPulse());
        JsonUtils.addString(object, "bp", health.getBp());
        JsonUtils.addFloat(object, "height", health.getHeight());
        JsonUtils.addFloat(object, "weight", health.getWeight());
        JsonUtils.addString(object, "vision", health.getVision());
        JsonUtils.addString(object, "hearing", health.getHearing());
        JsonUtils.addLong(object, "date", health.getDate());
        object.addProperty("selfExamination", health.isSelfExamination());
        JsonUtils.addString(object, "planetCode", health.getPlanetCode());
        object.addProperty("hasInfo", health.isHasInfo());
        JsonUtils.addString(object, "profileId", health.getProfileId());
        JsonUtils.addString(object, "creatorId", health.getProfileId());
        JsonUtils.addString(object, "gender", health.getGender());
        object.addProperty("age", health.getAge());
        JsonUtils.addJson(object, "conditions", new Gson().fromJson(health.getConditions(), JsonObject.class));
        return object;
    }
}
