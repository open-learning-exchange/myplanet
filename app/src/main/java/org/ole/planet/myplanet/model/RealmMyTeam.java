package org.ole.planet.myplanet.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;

import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyTeam extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
    private String teamId;
    private String name;
    private String description;
    private String requests;
    private String limit;
    private String status;

    public static void insertMyTeams(String userId, String teamId, JsonObject doc, Realm mRealm) {
        RealmMyTeam myTeams = mRealm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
        myTeams.setUserId(userId);
        myTeams.setTeamId(teamId);
        myTeams.setName(JsonUtils.getString("name", doc));
        myTeams.setDescription(JsonUtils.getString("description", doc));
        myTeams.setLimit(JsonUtils.getString("limit", doc));
        myTeams.setStatus(JsonUtils.getString("status", doc));
        myTeams.setRequests(JsonUtils.getJsonArray("requests", doc).toString());
    }


    public static JsonArray getMyTeamIds(Realm realm, String userId) {
        RealmResults<RealmMyTeam> teams = realm.where(RealmMyTeam.class).isNotEmpty("userId")
                .equalTo("userId", userId, Case.INSENSITIVE).findAll();

        JsonArray ids = new JsonArray();
        for (RealmMyTeam lib : teams
                ) {
            ids.add(lib.getTeamId());
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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


}
