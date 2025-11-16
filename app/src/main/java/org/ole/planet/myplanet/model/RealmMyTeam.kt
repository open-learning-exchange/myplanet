package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.DownloadUtils.openDownloadService
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils.getUrl

open class RealmMyTeam : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var courses: RealmList<String>? = null
    var teamId: String? = null
    var name: String? = null
    var userId: String? = null
    var description: String? = null
    var requests: String? = null
    var sourcePlanet: String? = null
    var limit = 0
    var createdDate: Long = 0
    var resourceId: String? = null
    var status: String? = null
    var teamType: String? = null
    var teamPlanetCode: String? = null
    var userPlanetCode: String? = null
    var parentCode: String? = null
    var docType: String? = null
    var title: String? = null
    var route: String? = null
    var services: String? = null
    var createdBy: String? = null
    var rules: String? = null
    var isLeader = false
    var type: String? = null
    var amount = 0
    var date: Long = 0
    var isPublic = false
    var updated = false
    var beginningBalance = 0
    var sales = 0
    var otherIncome = 0
    var wages = 0
    var otherExpenses = 0
    var startDate: Long = 0
    var endDate: Long = 0
    var updatedDate: Long = 0

    companion object {
        private val concatenatedLinks = ArrayList<String>()

        @JvmStatic
        fun populateTeamFields(doc: JsonObject, team: RealmMyTeam, includeCourses: Boolean = false) {
            team.userId = JsonUtils.getString("userId", doc)
            team.teamId = JsonUtils.getString("teamId", doc)
            team._rev = JsonUtils.getString("_rev", doc)
            team.name = JsonUtils.getString("name", doc)
            team.sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
            team.title = JsonUtils.getString("title", doc)
            team.description = JsonUtils.getString("description", doc)
            team.limit = JsonUtils.getInt("limit", doc)
            team.status = JsonUtils.getString("status", doc)
            team.teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
            team.createdDate = JsonUtils.getLong("createdDate", doc)
            team.resourceId = JsonUtils.getString("resourceId", doc)
            team.teamType = JsonUtils.getString("teamType", doc)
            team.route = JsonUtils.getString("route", doc)
            team.type = JsonUtils.getString("type", doc)
            team.services = JsonUtils.getString("services", doc)
            team.rules = JsonUtils.getString("rules", doc)
            team.parentCode = JsonUtils.getString("parentCode", doc)
            team.createdBy = JsonUtils.getString("createdBy", doc)
            team.userPlanetCode = JsonUtils.getString("userPlanetCode", doc)
            team.isLeader = JsonUtils.getBoolean("isLeader", doc)
            team.amount = JsonUtils.getInt("amount", doc)
            team.date = JsonUtils.getLong("date", doc)
            team.docType = JsonUtils.getString("docType", doc)
            team.isPublic = JsonUtils.getBoolean("public", doc)
            team.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
            team.sales = JsonUtils.getInt("sales", doc)
            team.otherIncome = JsonUtils.getInt("otherIncome", doc)
            team.wages = JsonUtils.getInt("wages", doc)
            team.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
            team.startDate = JsonUtils.getLong("startDate", doc)
            team.endDate = JsonUtils.getLong("endDate", doc)
            team.updatedDate = JsonUtils.getLong("updatedDate", doc)
            team.updated = JsonUtils.getBoolean("updated", doc)

            if (includeCourses) {
                val coursesArray = JsonUtils.getJsonArray("courses", doc)
                team.courses = RealmList()
                for (e in coursesArray) {
                    val id = e.asJsonObject["_id"].asString
                    if (!team.courses!!.contains(id)) {
                        team.courses!!.add(id)
                    }
                }
            }
        }

        private fun processDescription(description: String?) {
            val links = extractLinks(description ?: "")
            val baseUrl = getUrl()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            openDownloadService(context, concatenatedLinks, true)
        }

        @JvmStatic
        fun populateReportFields(doc: JsonObject, team: RealmMyTeam) {
            team.description = JsonUtils.getString("description", doc)
            team.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
            team.sales = JsonUtils.getInt("sales", doc)
            team.otherIncome = JsonUtils.getInt("otherIncome", doc)
            team.wages = JsonUtils.getInt("wages", doc)
            team.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
            team.startDate = JsonUtils.getLong("startDate", doc)
            team.endDate = JsonUtils.getLong("endDate", doc)
            team.updatedDate = JsonUtils.getLong("updatedDate", doc)
            team.updated = JsonUtils.getBoolean("updated", doc)
        }

        @JvmStatic
        fun insertMyTeams(doc: JsonObject, mRealm: Realm) {
            val status = JsonUtils.getString("status", doc)
            if (status == "archived") {
                return
            }

            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            myTeams?.let {
                populateTeamFields(doc, it, true)
                processDescription(it.description)
            }
        }

        @JvmStatic
        fun updateReports(doc: JsonObject, mRealm: Realm) {
            mRealm.executeTransactionAsync { realm ->
                val reportId = JsonUtils.getString("_id", doc)
                val report = realm.where(RealmMyTeam::class.java).equalTo("_id", reportId).findFirst()
                report?.let { populateReportFields(doc, it) }
            }
        }

        @JvmStatic
        fun getResourceIds(teamId: String?, realm: Realm): MutableList<String> {
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
        fun getTeamCreator(teamId: String?, realm: Realm?): String {
            val teams = realm?.where(RealmMyTeam::class.java)?.equalTo("teamId", teamId)?.findFirst()
            return teams?.userId ?: ""
        }

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject) {
            insertMyTeams(doc, mRealm)
        }

        @JvmStatic
        suspend fun requestToJoin(teamId: String?, userId: String?, userPlanetCode: String?, teamType: String?) {
            withContext(Dispatchers.IO) {
                Realm.getDefaultInstance().use { realm ->
                    realm.executeTransaction {
                        val team = it.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
                        team.docType = "request"
                        team.createdDate = Date().time
                        team.teamType = teamType
                        team.userId = userId
                        team.teamId = teamId
                        team.updated = true
                        team.teamPlanetCode = userPlanetCode
                        team.userPlanetCode = userPlanetCode
                    }
                }
            }
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
        fun getJoinedMemberCount(teamId: String, realm: Realm): Int {
            return getUsers(teamId, realm, "membership").size
        }

        @JvmStatic
        fun isTeamLeader(teamId: String?, userId: String?, realm: Realm): Boolean {
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
            val myTeam = query.findAll()
            val list = mutableListOf<RealmUserModel>()
            for (team in myTeam) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", team.userId)
                    .findFirst()
                if (model != null && !list.contains(model)) list.add(model)
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
            if (team.docType != "report" && team.docType != "request") {
                `object`.addProperty("limit", team.limit)
                `object`.addProperty("amount", team.amount)
                `object`.addProperty("date", team.date)
                `object`.addProperty("public", team.isPublic)
                `object`.addProperty("isLeader", team.isLeader)
            }
            if (team.docType != "request") {
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
            }
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

        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam> {
            val userId = settings?.getString("userId", "--") ?: "--"
            val list = mRealm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()

            val teamIds = list.map { it.teamId }.toTypedArray()
            return mRealm.where(RealmMyTeam::class.java)
                .`in`("_id", teamIds)
                .notEqualTo("status", "archived")
                .findAll()
        }
    }

    fun requested(userId: String?, mRealm: Realm): Boolean {
        val m = mRealm.where(RealmMyTeam::class.java)
            .equalTo("docType", "request")
            .equalTo("teamId", _id)
            .equalTo("userId", userId)
            .findAll()

        return m.isNotEmpty()
    }

    fun isMyTeam(userID: String?, mRealm: Realm): Boolean {
        return mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", userID)
            .equalTo("teamId", _id)
            .equalTo("docType", "membership")
            .count() > 0
    }

    fun leave(user: RealmUserModel?, mRealm: Realm) {
        val teams = mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", user?.id)
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
