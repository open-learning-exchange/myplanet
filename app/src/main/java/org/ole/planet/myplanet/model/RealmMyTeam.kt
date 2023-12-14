package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.annotations.PrimaryKey
import org.apache.commons.lang3.StringUtils
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date

open class RealmMyTeam : RealmObject() {
    @JvmField
    @PrimaryKey
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var courses: RealmList<String>? = null
    @JvmField
    var teamId: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var requests: String? = null
    @JvmField
    var sourcePlanet: String? = null
    @JvmField
    var limit = 0
    @JvmField
    var createdDate: Long = 0
    @JvmField
    var resourceId: String? = null
    @JvmField
    var status: String? = null
    @JvmField
    var teamType: String? = null
    @JvmField
    var teamPlanetCode: String? = null
    @JvmField
    var userPlanetCode: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var docType: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var route: String? = null
    @JvmField
    var services: String? = null
    @JvmField
    var createdBy: String? = null
    @JvmField
    var rules: String? = null
    @JvmField
    var isLeader = false
    @JvmField
    var type: String? = null
    @JvmField
    var amount = 0
    @JvmField
    var date: Long = 0
    @JvmField
    var isPublic = false
    @JvmField
    var updated = false

    fun requested(userId: String?, mRealm: Realm): Boolean {
        val m: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).equalTo("docType", "request")
            .equalTo("teamId", _id).equalTo("userId", userId).findAll()
        if (m.isNotEmpty()) {
            Utilities.log("Team " + m[0]._id + "  " + m[0].docType)
        }
        return m.isNotEmpty()
    }

    fun isMyTeam(userID: String?, mRealm: Realm): Boolean {
        Utilities.log("Is my team team id $_id")
        return mRealm.where(RealmMyTeam::class.java).equalTo("userId", userID)
            .equalTo("teamId", _id).equalTo("docType", "membership").count() > 0
    }

    fun leave(user: RealmUserModel, mRealm: Realm) {
        val teams: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).equalTo("userId", user.id).equalTo("teamId", _id)
            .equalTo("docType", "membership").findAll()
        for (team in teams) {
            removeTeam(team, mRealm)
        }
    }

    private fun removeTeam(team: RealmMyTeam, mRealm: Realm) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        team.deleteFromRealm()
        mRealm.commitTransaction()
    }

    companion object {
        @JvmStatic
        fun insertMyTeams(userId: String?, doc: JsonObject?, mRealm: Realm) {
            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            Utilities.log("$teamId Giddie")
            if (myTeams != null) {
                myTeams.userId = JsonUtils.getString("userId", doc)
                myTeams.teamId = JsonUtils.getString("teamId", doc)
                myTeams._rev = JsonUtils.getString("_rev", doc)
                myTeams.name = JsonUtils.getString("name", doc)
                myTeams.sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
                myTeams.title = JsonUtils.getString("title", doc)
                myTeams.description = JsonUtils.getString("description", doc)
                myTeams.limit = JsonUtils.getInt("limit", doc)
                myTeams.status = JsonUtils.getString("status", doc)
                myTeams.teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
                myTeams.createdDate = JsonUtils.getLong("createdDate", doc)
                myTeams.resourceId = JsonUtils.getString("resourceId", doc)
                myTeams.teamType = JsonUtils.getString("teamType", doc)
                myTeams.route = JsonUtils.getString("route", doc)
                myTeams.type = JsonUtils.getString("type", doc)
                myTeams.services = JsonUtils.getString("services", doc)
                myTeams.rules = JsonUtils.getString("rules", doc)
                myTeams.parentCode = JsonUtils.getString("parentCode", doc)
                myTeams.createdBy = JsonUtils.getString("createdBy", doc)
                myTeams.userPlanetCode = JsonUtils.getString("userPlanetCode", doc)
                myTeams.isLeader = JsonUtils.getBoolean("isLeader", doc)
                myTeams.amount = JsonUtils.getInt("amount", doc)
                myTeams.date = JsonUtils.getLong("date", doc)
                myTeams.docType = JsonUtils.getString("docType", doc)
                myTeams.isPublic = JsonUtils.getBoolean("public", doc)
                val coursesArray = JsonUtils.getJsonArray("courses", doc)
                myTeams.courses = RealmList()
                for (e in coursesArray) {
                    val id = e.asJsonObject["_id"].asString
                    if (!myTeams.courses!!.contains(id)) myTeams.courses!!.add(id)
                }
            }
        }

        @JvmStatic
        fun getResourceIds(teamId: String?, realm: Realm): List<String?> {
            val teams: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId).findAll()
            val ids: MutableList<String?> = ArrayList()
            for (team in teams) {
                if (!TextUtils.isEmpty(team.resourceId)) ids.add(team.resourceId)
            }
            return ids
        }

        fun getResourceIdsByUser(userId: String?, realm: Realm): List<String?> {
            val list: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java).equalTo("userId", userId)
                .equalTo("docType", "membership").findAll()
            val teamIds: MutableList<String?> = ArrayList()
            for (team in list) {
                if (!TextUtils.isEmpty(team.teamId)) teamIds.add(team.teamId)
            }
            val l2: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java).`in`("teamId", teamIds.toTypedArray<String?>())
                .equalTo("docType", "resourceLink").findAll()
            val ids: MutableList<String?> = ArrayList()
            for (team in l2) {
                if (!TextUtils.isEmpty(team.resourceId)) ids.add(team.resourceId)
            }
            return ids
        }

        @JvmStatic
        fun getTeamCreator(teamId: String?, realm: Realm): String? {
            val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
            return teams!!.userId
        }

        fun getTeamLeader(teamId: String?, realm: Realm): String {
            val team = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
                .equalTo("isLeader", true).findFirst()
            return if (team == null) "" else team.userId!!
        }

        fun insert(mRealm: Realm, doc: JsonObject?) {
            insertMyTeams("", doc, mRealm)
        }

        fun requestToJoin(teamId: String?, userModel: RealmUserModel, mRealm: Realm) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val team = mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            team.docType = "request"
            team.createdDate = Date().time
            team.teamType = "sync"
            team.userId = userModel.id
            team.teamId = teamId
            team.updated = true
            team.teamPlanetCode = userModel.planetCode
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun leaveTeam(teamId: String?, userModel: RealmUserModel, mRealm: Realm) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val team = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
                .equalTo("userId", userModel.id).findFirst()
            team!!.deleteFromRealm()
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun getRequestedMemeber(teamId: String?, realm: Realm): List<RealmUserModel> {
            return getUsers(teamId, realm, "request")
        }

        @JvmStatic
        fun getJoinedMemeber(teamId: String?, realm: Realm): List<RealmUserModel> {
            return getUsers(teamId, realm, "membership")
        }

        @JvmStatic
        fun isTeamLeader(teamId: String?, userId: String?, realm: Realm): Boolean {
            val team = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
                .equalTo("docType", "membership").equalTo("userId", userId)
                .equalTo("isLeader", true).findFirst()
            return team != null
        }

        @JvmStatic
        fun getUsers(teamId: String?, mRealm: Realm, docType: String?): List<RealmUserModel> {
            var query: RealmQuery<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            if (!TextUtils.isEmpty(docType)) {
                query = query.equalTo("docType", docType)
            }
            val myteam: List<RealmMyTeam> = query.findAll()
            val list: MutableList<RealmUserModel> = ArrayList()
            for (team in myteam) {
                val model = mRealm.where(RealmUserModel::class.java).equalTo("id", team.userId).findFirst()
                if (model != null && !list.contains(model)) list.add(model)
            }
            return list
        }

        @JvmStatic
        fun filterUsers(teamId: String?, user: String?, mRealm: Realm): List<RealmUserModel> {
            val myteam: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId).findAll()
            val list: MutableList<RealmUserModel> = ArrayList()
            for (team in myteam) {
                val model = mRealm.where(RealmUserModel::class.java).equalTo("id", team.userId).findFirst()
                if (model != null && model.name.contains(user!!)) list.add(model)
            }
            return list
        }

        @JvmStatic
        fun serialize(team: RealmMyTeam): JsonObject {
            val `object` = JsonObject()
            JsonUtils.addString(`object`, "_id", team._id)
            JsonUtils.addString(`object`, "_rev", team._rev)
            JsonUtils.addString(`object`, "teamId", team.teamId)
            `object`.addProperty("name", team.name)
            `object`.addProperty("userId", team.userId)
            `object`.addProperty("description", team.description)
            `object`.addProperty("limit", team.limit)
            `object`.addProperty("createdDate", team.createdDate)
            `object`.addProperty("status", team.status)
            `object`.addProperty("teamType", team.teamType)
            `object`.addProperty("teamPlanetCode", team.teamPlanetCode)
            `object`.addProperty("userPlanetCode", team.userPlanetCode)
            `object`.addProperty("parentCode", team.parentCode)
            `object`.addProperty("docType", team.docType)
            `object`.addProperty("isLeader", team.isLeader)
            `object`.addProperty("type", team.type)
            `object`.addProperty("amount", team.amount)
            `object`.addProperty("route", team.route)
            `object`.addProperty("date", team.date)
            `object`.addProperty("public", team.isPublic)
            `object`.addProperty("sourcePlanet", team.sourcePlanet)
            `object`.addProperty("services", team.services)
            `object`.addProperty("createdBy", team.createdBy)
            `object`.addProperty("resourceId", team.resourceId)
            `object`.addProperty("rules", team.rules)
            if (TextUtils.equals(team.teamType, "debit") || TextUtils.equals(team.teamType, "credit")) {
                `object`.addProperty("type", team.teamType)
            }
            return Gson().toJsonTree(`object`).asJsonObject
        }

        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences): List<RealmObject?> {
            val userId = settings.getString("userId", "--")
            val list: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).equalTo("userId", userId)
                .equalTo("docType", "membership").findAll()
            val teamList: MutableList<RealmObject?> = ArrayList()
            for (l in list) {
                val aa = mRealm.where(RealmMyTeam::class.java).equalTo("_id", l.teamId).findFirst()
                teamList.add(aa)
            }
            return teamList
        }
    }
}
