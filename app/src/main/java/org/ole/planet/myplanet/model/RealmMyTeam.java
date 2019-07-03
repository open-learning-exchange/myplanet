package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyTeam extends RealmObject {
    @PrimaryKey
    private String id;
    private RealmList<String> userId;
    private RealmList<String> courses;
    private String teamId;
    private String name;
    private String description;
    private String requests;
    private String limit;
    private String status;
    private String teamType;
    private String teamPlanetCode;

    public static void insertMyTeams(String userId, String teamId, JsonObject doc, Realm mRealm) {
        Utilities.log("Insert my team");
        RealmMyTeam myTeams = mRealm.where(RealmMyTeam.class).equalTo("id", teamId).findFirst();
        if (myTeams == null) {
            myTeams = mRealm.createObject(RealmMyTeam.class, teamId);
        }
        myTeams.setUserId(userId);
        myTeams.setTeamId(teamId);
        myTeams.setName(JsonUtils.getString("name", doc));
        myTeams.setDescription(JsonUtils.getString("description", doc));
        myTeams.setLimit(JsonUtils.getString("limit", doc));
        myTeams.setStatus(JsonUtils.getString("status", doc));
        myTeams.setStatus(JsonUtils.getString("teamPlanetCode", doc));
        myTeams.setStatus(JsonUtils.getString("teamType", doc));
        myTeams.setRequests(JsonUtils.getJsonArray("requests", doc).toString());
        JsonArray coursesArray = JsonUtils.getJsonArray("courses", doc);
        myTeams.courses = new RealmList<>();
        for (JsonElement e : coursesArray) {
            String id = e.getAsJsonObject().get("_id").getAsString();
            if (!myTeams.courses.contains(id))
                myTeams.courses.add(id);
        }
    }

    public String getTeamType() {
        return teamType;
    }

    public void setTeamType(String teamType) {
        this.teamType = teamType;
    }

    public String getTeamPlanetCode() {
        return teamPlanetCode;
    }

    public void setTeamPlanetCode(String teamPlanetCode) {
        this.teamPlanetCode = teamPlanetCode;
    }

    public static List<RealmObject> getMyTeamsByUserId(Realm mRealm, SharedPreferences settings) {
        RealmResults<RealmMyTeam> libs = mRealm.where(RealmMyTeam.class).findAll();
        return getMyTeamByUserId(settings.getString("userId", "--"), libs);
    }


    public static List<RealmObject> getMyTeamByUserId(String userId, List<RealmMyTeam> tm) {
        List<RealmObject> teams = new ArrayList<>();
        for (RealmMyTeam item : tm) {
            if (item.getUserId().contains(userId)) {
                teams.add(item);
            }
        }
        return teams;
    }

    public RealmList<String> getUserId() {
        return userId;
    }

    public RealmList<String> getCourses() {
        return courses;
    }

    public void setCourses(RealmList<String> courses) {
        this.courses = courses;
    }

    public void setUserId(RealmList<String> userId) {
        this.userId = userId;
    }

    public static JsonArray getMyTeamIds(Realm realm, String userId) {
        List<RealmObject> myLibraries = getMyTeamByUserId(userId, realm.where(RealmMyTeam.class).findAll());
        JsonArray ids = new JsonArray();
        for (RealmObject lib : myLibraries
        ) {
            ids.add(((RealmMyTeam) lib).getId());
        }
        return ids;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequests() {
        return requests;
    }

    public void setRequests(String requests) {
        this.requests = requests;
    }

    public String getLimit() {
        return limit;
    }

    public void setLimit(String limit) {
        this.limit = limit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setUserId(String userId) {
        Utilities.log("Set user id " + userId);
        if (this.userId == null) {
            this.userId = new RealmList<>();
        }

        if (!this.userId.contains(userId) && !TextUtils.isEmpty(userId))
            this.userId.add(userId);
    }
}
