package org.ole.planet.myplanet.model;

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
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
    @JvmField
    var beginningBalance = 0
    @JvmField
    var sales = 0
    @JvmField
    var otherIncome = 0
    @JvmField
    var wages = 0
    @JvmField
    var otherExpenses = 0
    @JvmField
    var startDate: Long = 0
    @JvmField
    var endDate: Long = 0
    @JvmField
    var updatedDate: Long = 0

    companion object {
        @JvmStatic
        fun insertMyTeams(doc: JsonObject, mRealm: Realm) {
            mRealm.executeTransactionAsync { realm ->
                val teamId = JsonUtils.getString("_id", doc)
                var myTeams = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                if (myTeams == null) {
                    myTeams = realm.createObject(RealmMyTeam::class.java, teamId)
                }
                Utilities.log(teamId)
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
                    myTeams.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
                    myTeams.sales = JsonUtils.getInt("sales", doc)
                    myTeams.otherIncome = JsonUtils.getInt("otherIncome", doc)
                    myTeams.wages = JsonUtils.getInt("wages", doc)
                    myTeams.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
                    myTeams.startDate = JsonUtils.getLong("startDate", doc)
                    myTeams.endDate = JsonUtils.getLong("endDate", doc)
                    myTeams.updatedDate = JsonUtils.getLong("updatedDate", doc)
                    val coursesArray = JsonUtils.getJsonArray("courses", doc)
                    myTeams.courses = RealmList()
                    for (e in coursesArray) {
                        val id = e.asJsonObject["_id"].asString
                        if (!myTeams.courses?.contains(id)!!) myTeams.courses?.add(id)
                    }
                }
            }
        }

        @JvmStatic
        fun insertReports(doc: JsonObject, mRealm: Realm) {
            mRealm.executeTransactionAsync { realm ->
                val teamId = JsonUtils.getString("_id", doc)
                var myTeams = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                if (myTeams == null) {
                    myTeams = realm.createObject(RealmMyTeam::class.java, teamId)
                }
                Utilities.log(teamId)
                if (myTeams != null) {
                    myTeams.teamId = JsonUtils.getString("teamId", doc)
                    myTeams.description = JsonUtils.getString("description", doc)
                    myTeams.teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
                    myTeams.createdDate = JsonUtils.getLong("createdDate", doc)
                    myTeams.teamType = JsonUtils.getString("teamType", doc)
                    myTeams.docType = JsonUtils.getString("docType", doc)
                    myTeams.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
                    myTeams.sales = JsonUtils.getInt("sales", doc)
                    myTeams.otherIncome = JsonUtils.getInt("otherIncome", doc)
                    myTeams.wages = JsonUtils.getInt("wages", doc)
                    myTeams.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
                    myTeams.startDate = JsonUtils.getLong("startDate", doc)
                    myTeams.endDate = JsonUtils.getLong("endDate", doc)
                    myTeams.updatedDate = JsonUtils.getLong("updatedDate", doc)
                    myTeams.updated = JsonUtils.getBoolean("updated", doc)
                }
            }
        }

        @JvmStatic
        fun updateReports(doc: JsonObject, mRealm: Realm) {
            mRealm.executeTransactionAsync { realm ->
                val reportId = JsonUtils.getString("_id", doc)
                val report = realm.where(RealmMyTeam::class.java).equalTo("_id", reportId).findFirst()
                report?.apply {
                    description = JsonUtils.getString("description", doc)
                    beginningBalance = JsonUtils.getInt("beginningBalance", doc)
                    sales = JsonUtils.getInt("sales", doc)
                    otherIncome = JsonUtils.getInt("otherIncome", doc)
                    wages = JsonUtils.getInt("wages", doc)
                    otherExpenses = JsonUtils.getInt("otherExpenses", doc)
                    startDate = JsonUtils.getLong("startDate", doc)
                    endDate = JsonUtils.getLong("endDate", doc)
                    updatedDate = JsonUtils.getLong("updatedDate", doc)
                    updated = JsonUtils.getBoolean("updated", doc)
                }
            }
        }

        @JvmStatic
        fun deleteReport(reportId: String, realm: Realm) {
            realm.executeTransactionAsync { transactionRealm ->
                val report = transactionRealm.where(RealmMyTeam::class.java).equalTo("_id", reportId).findFirst()
                report?.deleteFromRealm()
            }
        }

        @JvmStatic
        fun getResourceIds(teamId: String, realm: Realm): MutableList<String> {
            val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
            val ids = mutableListOf<String>()
            for (team in teams) {
                if (!team.resourceId.isNullOrBlank()) {
                    ids.add(team.resourceId!!)
                }
            }
            return ids
        }

        @JvmStatic
        fun getResourceIdsByUser(userId: String?, realm: Realm): MutableList<String> {
            val list = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()
            val teamIds = mutableListOf<String>()
            for (team in list) {
                if (!team.teamId.isNullOrBlank()) {
                    teamIds.add(team.teamId!!)
                }
            }
            val l2 = realm.where(RealmMyTeam::class.java)
                .`in`("teamId", teamIds.toTypedArray())
                .equalTo("docType", "resourceLink")
                .findAll()
            val ids = mutableListOf<String>()
            for (team in l2) {
                if (!team.resourceId.isNullOrBlank()) {
                    ids.add(team.resourceId!!)
                }
            }
            return ids
        }

        @JvmStatic
        fun getTeamCreator(teamId: String, realm: Realm): String {
            val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
            return teams?.userId ?: ""
        }

        @JvmStatic
        fun getTeamLeader(teamId: String?, realm: Realm): String {
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()
            return team?.userId ?: ""
        }

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject) {
            insertMyTeams(doc, mRealm)
        }

        @JvmStatic
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
        fun leaveTeam(teamId: String, userModel: RealmUserModel, mRealm: Realm) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val team = mRealm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userModel.id)
                .findFirst()
            team?.deleteFromRealm()
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun getRequestedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "request")
        }

        @JvmStatic
        fun getJoinedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "membership")
        }

        @JvmStatic
        fun isTeamLeader(teamId: String?, userId: String, realm: Realm): Boolean {
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("userId", userId)
                .equalTo("isLeader", true)
                .findFirst()
            return team != null
        }

        @JvmStatic
        fun getUsers(teamId: String?, mRealm: Realm, docType: String): MutableList<RealmUserModel> {
            var query = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            if (docType.isNotEmpty()) {
                query = query.equalTo("docType", docType)
            }
            val myteam = query.findAll()
            val list = mutableListOf<RealmUserModel>()
            for (team in myteam) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", team.userId)
                    .findFirst()
                if (model != null && !list.contains(model)) list.add(model)
            }
            return list
        }

        @JvmStatic
        fun filterUsers(teamId: String, user: String, mRealm: Realm): MutableList<RealmUserModel> {
            val myteam = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
            val list = mutableListOf<RealmUserModel>()
            for (team in myteam) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", team.userId)
                    .findFirst()
                if (model != null && model.name!!.contains(user)) list.add(model)
            }
            return list
        }

        @JvmStatic
        fun serialize(team: RealmMyTeam): JsonObject {
            val gson = Gson()
            val `object` = JsonObject()

            JsonUtils.addString(`object`, "_id", team._id)
            JsonUtils.addString(`object`, "_rev", team._rev)
            `object`.addProperty("name", team.name)
            `object`.addProperty("userId", team.userId)
            if (team.docType != "report") {
                `object`.addProperty("limit", team.limit)
                `object`.addProperty("amount", team.amount)
                `object`.addProperty("date", team.date)
                `object`.addProperty("public", team.isPublic)
                `object`.addProperty("isLeader", team.isLeader)
            }
            `object`.addProperty("createdDate", team.createdDate)
            `object`.addProperty("description", team.description)
            `object`.addProperty("beginningBalance", team.beginningBalance)
            `object`.addProperty("sales", team.sales)
            `object`.addProperty("otherIncome", team.otherIncome)
            `object`.addProperty("wages", team.wages)
            `object`.addProperty("otherExpenses", team.otherExpenses)
            `object`.addProperty("startDate", team.startDate)
            `object`.addProperty("endDate", team.endDate)
            `object`.addProperty("updatedDate", team.updatedDate)
            JsonUtils.addString(`object`, "teamId", team.teamId)
            `object`.addProperty("teamType", team.teamType)
            `object`.addProperty("teamPlanetCode", team.teamPlanetCode)
            `object`.addProperty("docType", team.docType)
            `object`.addProperty("status", team.status)
            `object`.addProperty("userPlanetCode", team.userPlanetCode)
            `object`.addProperty("parentCode", team.parentCode)

            `object`.addProperty("type", team.type)
            `object`.addProperty("route", team.route)
            `object`.addProperty("sourcePlanet", team.sourcePlanet)
            `object`.addProperty("services", team.services)
            `object`.addProperty("createdBy", team.createdBy)
            `object`.addProperty("resourceId", team.resourceId)
            `object`.addProperty("rules", team.rules)

            if (team.teamType == "debit" || team.teamType == "credit") {
                `object`.addProperty("type", team.teamType)
            }

            return JsonParser.parseString(gson.toJson(`object`)).asJsonObject
        }

        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences?): MutableList<RealmObject> {
            val userId = settings?.getString("userId", "--") ?: "--"
            val list = mRealm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()

            val teamList = mutableListOf<RealmObject>()
            for (l in list) {
                val aa = mRealm.where(RealmMyTeam::class.java)
                    .equalTo("_id", l.teamId)
                    .findFirst()
                aa?.let { teamList.add(it) }
            }
            return teamList
        }
    }

    fun requested(userId: String, mRealm: Realm): Boolean {
        val m = mRealm.where(RealmMyTeam::class.java)
            .equalTo("docType", "request")
            .equalTo("teamId", _id)
            .equalTo("userId", userId)
            .findAll()

        if (m.size > 0) {
            Utilities.log("Team ${m[0]?._id}  ${m[0]?.docType}")
        }
        return m.size > 0
    }

    fun isMyTeam(userID: String?, mRealm: Realm): Boolean {
        Utilities.log("Is my team team id $_id")
        return mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", userID)
            .equalTo("teamId", _id)
            .equalTo("docType", "membership")
            .count() > 0
    }

    fun leave(user: RealmUserModel, mRealm: Realm) {
        val teams = mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", user.id)
            .equalTo("teamId", this._id)
            .equalTo("docType", "membership")
            .findAll()

        for (team in teams) {
            if (team != null) {
                removeTeam(team, mRealm)
            }
        }
    }

    private fun removeTeam(team: RealmMyTeam, mRealm: Realm) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        team.deleteFromRealm()
        mRealm.commitTransaction()
    }
}
