package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyTeam extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private RealmList<String> courses;
    private String teamId;
    private String name;
    private String userId;
    private String description;
    private String requests;
    private String limit;
    private long createdDate;
    private String resourceId;
    private String status;
    private String teamType;
    private String teamPlanetCode;
    private String parentCode;
    private String docType;

    public static void insertMyTeams(String userId, JsonObject doc, Realm mRealm) {
        String teamId = JsonUtils.getString("_id", doc);
        RealmMyTeam myTeams = mRealm.where(RealmMyTeam.class).equalTo("id", teamId).findFirst();
        if (myTeams == null) {
            myTeams = mRealm.createObject(RealmMyTeam.class, teamId);
        }
        myTeams.setUser_id(JsonUtils.getString("userId", doc));
        myTeams.setTeamId(JsonUtils.getString("teamId", doc));
        myTeams.set_id(JsonUtils.getString("_id", doc));
        myTeams.set_rev(JsonUtils.getString("_rev", doc));
        myTeams.setName(JsonUtils.getString("name", doc));
        myTeams.setDescription(JsonUtils.getString("description", doc));
        myTeams.setLimit(JsonUtils.getString("limit", doc));
        myTeams.setStatus(JsonUtils.getString("status", doc));
        myTeams.setTeamPlanetCode(JsonUtils.getString("teamPlanetCode", doc));
        myTeams.setCreatedDate(JsonUtils.getLong("createdDate", doc));
        myTeams.setResourceId(JsonUtils.getString("resourceId", doc));
        myTeams.setTeamType(JsonUtils.getString("teamType", doc));
        myTeams.setParentCode(JsonUtils.getString("parentCode", doc));
//        myTeams.setRequests(new Gson().toJson(JsonUtils.getJsonArray("requests", doc)));
        myTeams.setDocType(JsonUtils.getString("docType", doc).toString());
        JsonArray coursesArray = JsonUtils.getJsonArray("courses", doc);
        myTeams.courses = new RealmList<>();
        for (JsonElement e : coursesArray) {
            String id = e.getAsJsonObject().get("_id").getAsString();
            if (!myTeams.courses.contains(id))
                myTeams.courses.add(id);
        }
    }

    public static List<String> getResourceIds(String teamId, Realm realm) {
        List<RealmMyTeam> teams = realm.where(RealmMyTeam.class).equalTo("teamId", teamId).findAll();
        List<String> ids = new ArrayList<>();
        for (RealmMyTeam team : teams) {
            if (!TextUtils.isEmpty(team.getResourceId()))
                ids.add(team.getResourceId());
        }
        return ids;
    }

    public static String getTeamCreator(String teamId, Realm realm) {
        List<RealmMyTeam> teams = realm.where(RealmMyTeam.class).equalTo("teamId", teamId).findAll();
        if(!teams.isEmpty()) {
            return teams.get(0).userId;
        }
        return "";
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public static void insert(Realm mRealm, JsonObject doc) {
        insertMyTeams("", doc, mRealm);
    }

    public static void requestToJoin(String teamId, RealmUserModel userModel, Realm mRealm) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmMyTeam team = mRealm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
        team.setDocType("request");
        team.setCreatedDate(new Date().getTime());
        team.setTeamType("sync");
        team.setUser_id(userModel.getId());
        team.setTeamId(teamId);
        team.setTeamPlanetCode(userModel.getPlanetCode());
        mRealm.commitTransaction();
    }

    public static void leaveTeam(String teamId, RealmUserModel userModel, Realm mRealm) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.getId()).findFirst();
        team.deleteFromRealm();
        mRealm.commitTransaction();

    }

    public static List<RealmUserModel> getRequestedMemeber(String teamId, Realm realm){
        return getUsers(teamId, realm,"request");
    }
    public static List<RealmUserModel> getJoinedMemeber(String teamId, Realm realm){
        return getUsers(teamId, realm,"membership");
    }

    public static List<RealmUserModel> getUsers(String teamId, Realm mRealm, String docType) {
        RealmQuery query = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId);
        if (!TextUtils.isEmpty(docType)) {
          query =   query.equalTo("docType", docType);
        }
        List<RealmMyTeam> myteam = query.findAll();
        List<RealmUserModel> list = new ArrayList<>();
        for (RealmMyTeam team : myteam) {
            RealmUserModel model = mRealm.where(RealmUserModel.class).equalTo("id", team.getUser_id()).findFirst();
            if (model != null && !list.contains(model))
                list.add(model);
        }
        return list;
    }

    public static List<RealmUserModel> filterUsers(String teamId, String user, Realm mRealm) {
        List<RealmMyTeam> myteam = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).findAll();
        List<RealmUserModel> list = new ArrayList<>();
        for (RealmMyTeam team : myteam) {
            RealmUserModel model = mRealm.where(RealmUserModel.class).equalTo("id", team.getUser_id()).findFirst();
            if (model != null && (model.getName().contains(user)))
                list.add(model);
        }
        return list;
    }

    public static JsonObject serialize(RealmMyTeam team) {
        JsonObject object = new JsonObject();
        object.addProperty("teamId", team.getTeamId());
        object.addProperty("name", team.getName());
        object.addProperty("userId", team.getUser_id());
        object.addProperty("description", team.getDescription());
        object.addProperty("limit", team.getLimit());
        object.addProperty("createdDate", team.getCreatedDate());
        object.addProperty("status", team.getStatus());
        object.addProperty("teamType", team.getTeamType());
        object.addProperty("teamPlanetCode", team.getTeamPlanetCode());
        object.addProperty("parentCode", team.getParentCode());
        object.addProperty("docType", team.getDocType());
        return object;
    }


    public void setUser_id(String user_id) {
        this.userId = user_id;
    }

    public String getUser_id() {
        return this.userId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
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
        String userId = settings.getString("userId", "--");
        List<RealmMyTeam> list = mRealm.where(RealmMyTeam.class).equalTo("userId", userId).equalTo("docType", "membership").findAll();
        List<RealmObject> teamList = new ArrayList<>();
        for (RealmMyTeam l : list) {
            RealmMyTeam aa = mRealm.where(RealmMyTeam.class).equalTo("_id", l.getTeamId()).findFirst();
            teamList.add(aa);
        }
        return teamList;
    }


    public RealmList<String> getCourses() {
        return courses;
    }

    public void setCourses(RealmList<String> courses) {
        this.courses = courses;
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

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public boolean requested(String userId, Realm mRealm) {
        List<RealmMyTeam> m = mRealm.where(RealmMyTeam.class).equalTo("docType", "request").equalTo("teamId", this._id).equalTo("userId", userId).findAll();
        if (m.size() > 0) {
            Utilities.log("Team " + m.get(0).get_id() + "  " + m.get(0).getDocType());
        }
        return m.size() > 0;
    }

    public boolean isMyTeam(String userID, Realm mRealm) {
        Utilities.log("Is my team team id " + this._id);
        return mRealm.where(RealmMyTeam.class).equalTo("userId", userID).equalTo("teamId", this._id).equalTo("docType", "membership").count() > 0;
    }

    public void leave(RealmUserModel user, Realm mRealm) {
        List<RealmMyTeam> teams = mRealm.where(RealmMyTeam.class).equalTo("userId", user.getId()).equalTo("teamId", this._id).equalTo("docType", "membership").findAll();
        for (RealmMyTeam team : teams) {
            if (team != null) {
                removeTeam(team, mRealm);
            }
        }
    }

    private void removeTeam(RealmMyTeam team, Realm mRealm) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        team.deleteFromRealm();
        mRealm.commitTransaction();
    }
}
