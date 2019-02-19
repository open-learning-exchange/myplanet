package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmAchievement extends RealmObject {
    private RealmList<String> otherInfo;

    private RealmList<String> achievements;

    private String purpose;

    private String achievementsHeader;

    private String sendToNation;

    private String _rev;

    @PrimaryKey
    private String _id;

    private String goals;

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getAchievementsHeader() {
        return achievementsHeader;
    }

    public void setAchievementsHeader(String achievementsHeader) {
        this.achievementsHeader = achievementsHeader;
    }

    public String getSendToNation() {
        return sendToNation;
    }

    public void setSendToNation(String sendToNation) {
        this.sendToNation = sendToNation;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getGoals() {
        return goals;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }


    public static void insertAchievement(Realm mRealm, JsonObject act) {
        Utilities.log("Insert achievement " + new Gson().toJson(act));
        RealmAchievement achievement = mRealm.where(RealmAchievement.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (achievement == null)
            achievement = mRealm.createObject(RealmAchievement.class, JsonUtils.getString("_id", act));
        achievement.set_rev(JsonUtils.getString("_rev", act));
        achievement.setPurpose(JsonUtils.getString("purpose", act));
        achievement.setGoals(JsonUtils.getString("goals", act));
        achievement.setAchievementsHeader(JsonUtils.getString("achievementsHeader", act));
        achievement.setOtherInfo(JsonUtils.getJsonArray("otherInfo", act));
        achievement.setAchievements(JsonUtils.getJsonArray("achievements", act));

    }

    public RealmList<String> getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(JsonArray of) {
        if (this.otherInfo == null) {
            otherInfo = new RealmList<String>();
        }
        for (JsonElement el : of
        ) {
            String e = new Gson().toJson(el);
            if (!otherInfo.contains(e))
                otherInfo.add(e);
        }
    }

    public RealmList<String> getAchievements() {
        return achievements;
    }

    public void setAchievements(JsonArray ac) {
        if (this.achievements == null) {
            achievements = new RealmList<String>();
        }
        for (JsonElement el : ac) {
            String achi = el.getAsString();
            if (!achievements.contains(achi))
                achievements.add(achi);
        }
    }
}