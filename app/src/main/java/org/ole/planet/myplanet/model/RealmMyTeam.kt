package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import org.ole.planet.myplanet.utilities.Utilities.openDownloadService
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

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
        private val teamDataList: MutableList<Array<String>> = mutableListOf()
        val reportsDataList: MutableList<Array<String>> = mutableListOf()
        private val concatenatedLinks = ArrayList<String>()

        fun insertMyTeams(doc: JsonObject, mRealm: Realm, string: String) {
            Log.d("okuro", "source: $string")
            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            if (myTeams != null) {
                myTeams.userId = JsonUtils.getString("userId", doc)
                myTeams.teamId = JsonUtils.getString("teamId", doc)
                myTeams._rev = JsonUtils.getString("_rev", doc)
                myTeams.name = JsonUtils.getString("name", doc)
                myTeams.sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
                myTeams.title = JsonUtils.getString("title", doc)
                myTeams.description = JsonUtils.getString("description", doc)
                val links = extractLinks(JsonUtils.getString("description", doc))
                val baseUrl = getUrl()
                for (link in links) {
                    val concatenatedLink = "$baseUrl/$link"
                    concatenatedLinks.add(concatenatedLink)
                }
                openDownloadService(MainApplication.context, concatenatedLinks, true)
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
                    if (!myTeams.courses?.contains(id)!!) {
                        myTeams.courses?.add(id)
                    }
                }
            }
            val csvRow = arrayOf(
                JsonUtils.getString("userId", doc),
                JsonUtils.getString("teamId", doc),
                JsonUtils.getString("_rev", doc),
                JsonUtils.getString("name", doc),
                JsonUtils.getString("sourcePlanet", doc),
                JsonUtils.getString("title", doc),
                JsonUtils.getString("description", doc),
                JsonUtils.getInt("limit", doc).toString(),
                JsonUtils.getString("status", doc),
                JsonUtils.getString("teamPlanetCode", doc),
                JsonUtils.getLong("createdDate", doc).toString(),
                JsonUtils.getString("resourceId", doc),
                JsonUtils.getString("teamType", doc),
                JsonUtils.getString("route", doc),
                JsonUtils.getString("type", doc),
                JsonUtils.getString("services", doc),
                JsonUtils.getString("rules", doc),
                JsonUtils.getString("parentCode", doc),
                JsonUtils.getString("createdBy", doc),
                JsonUtils.getString("userPlanetCode", doc),
                JsonUtils.getBoolean("isLeader", doc).toString(),
                JsonUtils.getInt("amount", doc).toString(),
                JsonUtils.getLong("date", doc).toString(),
                JsonUtils.getString("docType", doc),
                JsonUtils.getBoolean("public", doc).toString(),
                JsonUtils.getInt("beginningBalance", doc).toString(),
                JsonUtils.getInt("sales", doc).toString(),
                JsonUtils.getInt("otherIncome", doc).toString(),
                JsonUtils.getInt("wages", doc).toString(),
                JsonUtils.getInt("otherExpenses", doc).toString(),
                JsonUtils.getLong("startDate", doc).toString(),
                JsonUtils.getLong("endDate", doc).toString(),
                JsonUtils.getLong("updatedDate", doc).toString(),
                JsonUtils.getJsonArray("courses", doc).toString()
            )
            teamDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("userId", "teamId", "teamId_rev", "name", "sourcePlanet", "title", "description", "limit", "status", "teamPlanetCode", "createdDate", "resourceId", "teamType", "route", "type", "services", "rules", "parentCode", "createdBy", "userPlanetCode", "isLeader", "amount", "date", "docType", "public", "beginningBalance", "sales", "otherIncome", "wages", "otherExpenses", "startDate", "endDate", "updatedDate", "courses"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun teamWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/team.csv", teamDataList)
        }

        fun insertReports(doc: JsonObject, mRealm: Realm) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
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
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("teamId", doc),
                JsonUtils.getString("description", doc),
                JsonUtils.getString("teamPlanetCode", doc),
                JsonUtils.getLong("createdDate", doc).toString(),
                JsonUtils.getString("teamType", doc),
                JsonUtils.getString("docType", doc),
                JsonUtils.getInt("beginningBalance", doc).toString(),
                JsonUtils.getInt("sales", doc).toString(),
                JsonUtils.getInt("otherIncome", doc).toString(),
                JsonUtils.getInt("wages", doc).toString(),
                JsonUtils.getInt("otherExpenses", doc).toString(),
                JsonUtils.getLong("startDate", doc).toString(),
                JsonUtils.getLong("endDate", doc).toString(),
                JsonUtils.getLong("updatedDate", doc).toString(),
                JsonUtils.getBoolean("updated", doc).toString()
            )
            reportsDataList.add(csvRow)
        }

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

        fun deleteReport(reportId: String, realm: Realm) {
            realm.executeTransactionAsync { transactionRealm ->
                val report = transactionRealm.where(RealmMyTeam::class.java).equalTo("_id", reportId).findFirst()
                report?.deleteFromRealm()
            }
        }

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

        fun getTeamCreator(teamId: String?, realm: Realm?): String {
            val teams = realm?.where(RealmMyTeam::class.java)?.equalTo("teamId", teamId)?.findFirst()
            return teams?.userId ?: ""
        }

        fun getTeamLeader(teamId: String?, realm: Realm): String {
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()
            return team?.userId ?: ""
        }

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject) {
            insertMyTeams(doc, mRealm, "insert")
        }

        fun requestToJoin(teamId: String?, userModel: RealmUserModel?, mRealm: Realm) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val team = mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            team.docType = "request"
            team.createdDate = Date().time
            team.teamType = "sync"
            team.userId = userModel?.id
            team.teamId = teamId
            team.updated = true
            team.teamPlanetCode = userModel?.planetCode
            mRealm.commitTransaction()
        }

        fun getRequestedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "request")
        }

        fun getJoinedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "membership")
        }

        fun isTeamLeader(teamId: String?, userId: String?, realm: Realm): Boolean {
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("userId", userId)
                .equalTo("isLeader", true)
                .findFirst()
            return team != null
        }

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

        fun filterUsers(teamId: String?, user: String, mRealm: Realm): MutableList<RealmUserModel> {
            val myTeam = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
            val list = mutableListOf<RealmUserModel>()
            for (team in myTeam) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", team.userId)
                    .findFirst()
                if (model != null && model.name?.contains(user) == true) {
                    list.add(model)
                }
            }
            return list
        }

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

        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam> {
            val userId = settings?.getString("userId", "--") ?: "--"
            val list = mRealm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()

            val teamIds = list.map { it.teamId }.toTypedArray()
            return mRealm.where(RealmMyTeam::class.java)
                .`in`("_id", teamIds)
                .findAll()
        }
    }

    fun requested(userId: String?, mRealm: Realm): Boolean {
        val m = mRealm.where(RealmMyTeam::class.java)
            .equalTo("docType", "request")
            .equalTo("teamId", _id)
            .equalTo("userId", userId)
            .findAll()

        return m.size > 0
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
