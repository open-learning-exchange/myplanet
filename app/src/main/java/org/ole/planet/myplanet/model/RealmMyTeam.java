package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmQuery;
import io.realm.annotations.PrimaryKey;

import static org.apache.commons.lang3.StringUtils.trim;

public class RealmMyTeam extends RealmObject {
    @PrimaryKey
    private String _id;
    private String _rev;
    private RealmList<String> courses;
    private String teamId;
    private String name;
    private String userId;
    private String description;
    private String requests;
    private String sourcePlanet;
    private int limit;
    private long createdDate;
    private String resourceId;
    private String status;
    private String teamType;
    private String teamPlanetCode;
    private String userPlanetCode;
    private String parentCode;
    private String docType;
    private String title;
    private String route;
    private String services;
    private String createdBy;
    private String rules;
    private boolean isLeader;
    private String type;
    private int amount;
    private long date;
    private boolean isPublic;
    private boolean updated;

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public static void insertMyTeams(String userId, JsonObject doc, Realm mRealm) {
        String teamId = JsonUtils.getString("_id", doc);
        RealmMyTeam myTeams = mRealm.where(RealmMyTeam.class).equalTo("_id", teamId).findFirst();
        if (myTeams == null) {
            myTeams = mRealm.createObject(RealmMyTeam.class, teamId);
        }
        Utilities.log(teamId);
        myTeams.setUser_id(JsonUtils.getString("userId", doc));
        myTeams.setTeamId(JsonUtils.getString("teamId", doc));
        myTeams.set_rev(JsonUtils.getString("_rev", doc));
        myTeams.setName(JsonUtils.getString("name", doc));
        myTeams.setSourcePlanet(JsonUtils.getString("sourcePlanet", doc));
        myTeams.setTitle(JsonUtils.getString("title", doc));
        myTeams.setDescription(JsonUtils.getString("description", doc));
        myTeams.setLimit(JsonUtils.getInt("limit", doc));
        myTeams.setStatus(JsonUtils.getString("status", doc));
        myTeams.setTeamPlanetCode(JsonUtils.getString("teamPlanetCode", doc));
        myTeams.setCreatedDate(JsonUtils.getLong("createdDate", doc));
        myTeams.setResourceId(JsonUtils.getString("resourceId", doc));
        myTeams.setTeamType(JsonUtils.getString("teamType", doc));
        myTeams.setRoute(JsonUtils.getString("route", doc));
        myTeams.setType(JsonUtils.getString("type", doc));
        myTeams.setServices(JsonUtils.getString("services", doc));
        myTeams.setRules(JsonUtils.getString("rules", doc));
        myTeams.setParentCode(JsonUtils.getString("parentCode", doc));
        myTeams.setCreatedBy(JsonUtils.getString("createdBy", doc));
        myTeams.setUserPlanetCode(JsonUtils.getString("userPlanetCode", doc));
        myTeams.setLeader(JsonUtils.getBoolean("isLeader", doc));
        myTeams.setAmount(JsonUtils.getInt("amount", doc));
        myTeams.setDate(JsonUtils.getLong("date", doc));
//        myTeams.setRequests(new Gson().toJson(JsonUtils.getJsonArray("requests", doc)));
        myTeams.setDocType(JsonUtils.getString("docType", doc));
        myTeams.setPublic(JsonUtils.getBoolean("public", doc));
        JsonArray coursesArray = JsonUtils.getJsonArray("courses", doc);
        myTeams.courses = new RealmList<>();
        for (JsonElement e : coursesArray) {
            String id = e.getAsJsonObject().get("_id").getAsString();
            if (!myTeams.courses.contains(id))
                myTeams.courses.add(id);
        }
    }

    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }


    public String getUserPlanetCode() {
        return userPlanetCode;
    }

    public void setUserPlanetCode(String userPlanetCode) {
        this.userPlanetCode = userPlanetCode;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getServices() {
        return services;
    }

    public void setServices(String services) {
        this.services = services;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void setLeader(boolean leader) {
        isLeader = leader;
    }


    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }


    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
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


    public static List<String> getResourceIdsByUser(String userId, Realm realm) {
        List<RealmMyTeam> list = realm.where(RealmMyTeam.class).equalTo("userId", userId).equalTo("docType", "membership").findAll();
        List<String> teamIds = new ArrayList<>();
        for (RealmMyTeam team : list) {
            if (!TextUtils.isEmpty(team.getTeamId()))
                teamIds.add(team.getTeamId());
        }
        List<RealmMyTeam> l2 = realm.where(RealmMyTeam.class).in("teamId", teamIds.toArray(new String[0])).equalTo("docType", "resourceLink").findAll();
        List<String> ids = new ArrayList<>();
        for (RealmMyTeam team : l2) {
            if (!TextUtils.isEmpty(team.getResourceId()))
                ids.add(team.getResourceId());
        }

        return ids;
    }

    public static String getTeamCreator(String teamId, Realm realm) {
        RealmMyTeam teams = realm.where(RealmMyTeam.class).equalTo("teamId", teamId).findFirst();
        return teams.getUserId();
    }

    public static String getTeamLeader(String teamId, Realm realm) {
        RealmMyTeam team = realm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("isLeader", true).findFirst();
        return team == null ? "" : team.getUserId();
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
        RealmMyTeam team = mRealm.createObject(RealmMyTeam.class, AndroidDecrypter.generateIv());
        team.setDocType("request");
        team.setCreatedDate(new Date().getTime());
        team.setTeamType("sync");
        team.setUser_id(userModel.getId());
        team.setTeamId(teamId);
        team.setUpdated(true);
        team.setTeamPlanetCode(userModel.getPlanetCode());
        mRealm.commitTransaction();
    }

    public String getSourcePlanet() {
        return sourcePlanet;
    }

    public void setSourcePlanet(String sourcePlanet) {
        this.sourcePlanet = sourcePlanet;
    }

    public static void leaveTeam(String teamId, RealmUserModel userModel, Realm mRealm) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.getId()).findFirst();
        team.deleteFromRealm();
        mRealm.commitTransaction();

    }

    public static List<RealmUserModel> getRequestedMemeber(String teamId, Realm realm) {
        return getUsers(teamId, realm, "request");
    }

    public static List<RealmUserModel> getJoinedMemeber(String teamId, Realm realm) {
        return getUsers(teamId, realm, "membership");
    }

    public static boolean isTeamLeader(String teamId, String userId, Realm realm) {
        RealmMyTeam team = realm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("docType", "membership").equalTo("userId", userId).equalTo("isLeader", true).findFirst();
        return team != null;

    }


    public static List<RealmUserModel> getUsers(String teamId, Realm mRealm, String docType) {
        RealmQuery query = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId);
        if (!TextUtils.isEmpty(docType)) {
            query = query.equalTo("docType", docType);
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

    public String getTitle() {
        return trim(title);
    }

    public void setTitle(String title) {
        this.title = trim(title);
    }

    public static JsonObject serialize(RealmMyTeam team) {
        JsonObject object = new JsonObject();
        JsonUtils.addString(object, "_id", team.get_id());
        JsonUtils.addString(object, "_rev", team.get_rev());
        JsonUtils.addString(object, "teamId", team.getTeamId());
        object.addProperty("name", team.getName());
        object.addProperty("userId", team.getUser_id());
        object.addProperty("description", team.getDescription());
        object.addProperty("limit", team.getLimit());
        object.addProperty("createdDate", team.getCreatedDate());
        object.addProperty("status", team.getStatus());
        object.addProperty("teamType", team.getTeamType());
        object.addProperty("teamPlanetCode", team.getTeamPlanetCode());
        object.addProperty("userPlanetCode", team.getUserPlanetCode());
        object.addProperty("parentCode", team.getParentCode());
        object.addProperty("docType", team.getDocType());
        object.addProperty("isLeader", team.isLeader());
        object.addProperty("type", team.getType());
        object.addProperty("amount", team.getAmount());
        object.addProperty("route", team.getRoute());
        object.addProperty("date", team.getDate());
        object.addProperty("public", team.isPublic());
        object.addProperty("sourcePlanet", team.getSourcePlanet());
        object.addProperty("services", team.getServices());
        object.addProperty("createdBy", team.getCreatedBy());
        object.addProperty("resourceId", team.getResourceId());
        object.addProperty("rules", team.getRules());
        if (TextUtils.equals(team.getTeamType(), "debit") || TextUtils.equals(team.getTeamType(), "credit")) {
            object.addProperty("type", team.getTeamType());
        }
        return new Gson().toJsonTree(object).getAsJsonObject();
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


    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }


    public String getName() {
        return trim(name);
    }

    public void setName(String name) {
        this.name = trim(name);
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

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
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
