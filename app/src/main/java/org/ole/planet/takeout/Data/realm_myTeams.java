package org.ole.planet.takeout.Data;

import com.google.gson.JsonObject;
import java.util.UUID;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_myTeams extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
    private String teamId;
    private String name;
    private String description;
    private String requests;
    private String limit;
    private String status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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


    public static void insertMyTeams(String userId, String teamId, JsonObject doc, Realm mRealm) {
        realm_myTeams myTeams = mRealm.createObject(realm_myTeams.class, UUID.randomUUID().toString());
        myTeams.setUserId(userId);
        myTeams.setTeamId(teamId);
        myTeams.setName(doc.get("name").getAsString());
        myTeams.setDescription(doc.get("description").getAsString());
        myTeams.setLimit(doc.get("limit").getAsString());
        myTeams.setStatus(doc.get("status").getAsString());
        myTeams.setRequests(doc.get("requests").getAsJsonArray().toString());

    }


}
