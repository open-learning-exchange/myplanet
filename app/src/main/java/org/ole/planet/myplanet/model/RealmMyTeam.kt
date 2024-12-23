package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import org.ole.planet.myplanet.utilities.Utilities.openDownloadService
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

//import org.ole.planet.myplanet.utilities.JsonUtils
class RealmMyTeam : RealmObject {
    @PrimaryKey
    var _id: String = ""
    var _rev: String = ""
    var courses: RealmList<String> = realmListOf()
    var teamId: String = ""
    var name: String = ""
    var userId: String = ""
    var description: String = ""
    var requests: String = ""
    var sourcePlanet: String = ""
    var limit: Int = 0
    var createdDate: Long = 0
    var resourceId: String = ""
    var status: String = ""
    var teamType: String = ""
    var teamPlanetCode: String = ""
    var userPlanetCode: String = ""
    var parentCode: String = ""
    var docType: String = ""
    var title: String = ""
    var route: String = ""
    var services: String = ""
    var createdBy: String = ""
    var rules: String = ""
    var isLeader: Boolean = false
    var type: String = ""
    var amount: Int = 0
    var date: Long = 0
    var isPublic: Boolean = false
    var updated: Boolean = false
    var beginningBalance: Int = 0
    var sales: Int = 0
    var otherIncome: Int = 0
    var wages: Int = 0
    var otherExpenses: Int = 0
    var startDate: Long = 0
    var endDate: Long = 0
    var updatedDate: Long = 0

    companion object {
        private val teamDataList: MutableList<Array<String>> = mutableListOf()
        val reportsDataList: MutableList<Array<String>> = mutableListOf()
        private val concatenatedLinks = ArrayList<String>()

        fun insertMyTeams(doc: JsonObject, realm: Realm) {
            val teamID = JsonUtils.getString("_id", doc)
            realm.writeBlocking {
                val myTeams = query<RealmMyTeam>("_id == $0", teamID).first().find()
                    ?: RealmMyTeam().apply { this._id = teamID }

                myTeams.apply {
                    userId = JsonUtils.getString("userId", doc)
                    teamId = JsonUtils.getString("teamId", doc)
                    _rev = JsonUtils.getString("_rev", doc)
                    name = JsonUtils.getString("name", doc)
                    sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
                    title = JsonUtils.getString("title", doc)
                    description = JsonUtils.getString("description", doc)

                    val links = extractLinks(JsonUtils.getString("description", doc))
                    val baseUrl = getUrl()
                    concatenatedLinks.clear()
                    concatenatedLinks.addAll(links.map { "$baseUrl/$it" })
                    openDownloadService(context, concatenatedLinks, true)

                    limit = JsonUtils.getInt("limit", doc)
                    status = JsonUtils.getString("status", doc)
                    teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
                    createdDate = JsonUtils.getLong("createdDate", doc)
                    resourceId = JsonUtils.getString("resourceId", doc)
                    teamType = JsonUtils.getString("teamType", doc)
                    route = JsonUtils.getString("route", doc)
                    type = JsonUtils.getString("type", doc)
                    services = JsonUtils.getString("services", doc)
                    rules = JsonUtils.getString("rules", doc)
                    parentCode = JsonUtils.getString("parentCode", doc)
                    createdBy = JsonUtils.getString("createdBy", doc)
                    userPlanetCode = JsonUtils.getString("userPlanetCode", doc)
                    isLeader = JsonUtils.getBoolean("isLeader", doc)
                    amount = JsonUtils.getInt("amount", doc)
                    date = JsonUtils.getLong("date", doc)
                    docType = JsonUtils.getString("docType", doc)
                    isPublic = JsonUtils.getBoolean("public", doc)
                    beginningBalance = JsonUtils.getInt("beginningBalance", doc)
                    sales = JsonUtils.getInt("sales", doc)
                    otherIncome = JsonUtils.getInt("otherIncome", doc)
                    wages = JsonUtils.getInt("wages", doc)
                    otherExpenses = JsonUtils.getInt("otherExpenses", doc)
                    startDate = JsonUtils.getLong("startDate", doc)
                    endDate = JsonUtils.getLong("endDate", doc)
                    updatedDate = JsonUtils.getLong("updatedDate", doc)

                    val coursesArray = JsonUtils.getJsonArray("courses", doc)
                    courses.clear()
                    coursesArray.forEach { element ->
                        val id = element.asJsonObject["_id"].asString
                        if (!courses.contains(id)) {
                            courses.add(id)
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
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(
                        arrayOf("userId", "teamId", "teamId_rev", "name", "sourcePlanet", "title",
                            "description", "limit", "status", "teamPlanetCode", "createdDate",
                            "resourceId", "teamType", "route", "type", "services", "rules", "parentCode",
                            "createdBy", "userPlanetCode", "isLeader", "amount", "date", "docType",
                            "public", "beginningBalance", "sales", "otherIncome", "wages",
                            "otherExpenses", "startDate", "endDate", "updatedDate", "courses"
                        )
                    )
                    for (row in data) {
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun teamWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/team.csv", teamDataList)
        }

        fun insertReports(doc: JsonObject, realm: Realm) {
            realm.writeBlocking {
                val teamId = JsonUtils.getString("_id", doc)
                val myTeams = query<RealmMyTeam>("_id == $0", teamId).first().find()
                    ?: RealmMyTeam().apply { this._id = teamId }

                myTeams.apply {
                    this.teamId = JsonUtils.getString("teamId", doc)
                    description = JsonUtils.getString("description", doc)
                    teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
                    createdDate = JsonUtils.getLong("createdDate", doc)
                    teamType = JsonUtils.getString("teamType", doc)
                    docType = JsonUtils.getString("docType", doc)
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

        fun updateReports(doc: JsonObject, realm: Realm) {
            realm.writeBlocking {
                val reportId = JsonUtils.getString("_id", doc)
                val report = query<RealmMyTeam>("_id == $0", reportId).first().find()
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
            realm.writeBlocking {
                query<RealmMyTeam>("_id == $0", reportId).first().find()?.let { delete(it) }
            }
        }

        fun getResourceIds(teamId: String, realm: Realm): List<String> {
            return realm.query<RealmMyTeam>("teamId == $0", teamId).find().map {
                it.resourceId
            }.filter { it.isNotBlank() }
        }

        fun getResourceIdsByUser(userId: String, realm: Realm): List<String> {
            val teamIds = realm.query<RealmMyTeam>("userId == $0 AND docType == 'membership'", userId).find().map { it.teamId }

            return realm.query<RealmMyTeam>("teamId IN $0 AND docType == 'resourceLink'", teamIds).find().map {
                it.resourceId
            }.filter { it.isNotBlank() }
        }

        fun getTeamCreator(teamId: String, realm: Realm): String {
            return realm.query<RealmMyTeam>("teamId == $0", teamId).first().find()?.userId ?: ""
        }

        fun getTeamLeader(teamId: String, realm: Realm): String {
            return realm.query<RealmMyTeam>("teamId == $0 AND isLeader == true", teamId).first().find()?.userId ?: ""
        }

        fun insert(realm: Realm, doc: JsonObject) {
            insertMyTeams(doc, realm)
        }

        fun requestToJoin(teamId: String, userModel: RealmUserModel, realm: Realm) {
            realm.writeBlocking {
                copyToRealm(RealmMyTeam().apply {
                    this._id = AndroidDecrypter.generateIv()
                    this.docType = "request"
                    this.createdDate = Date().time
                    this.teamType = "sync"
                    this.userId = userModel.id
                    this.teamId = teamId
                    this.updated = true
                    this.teamPlanetCode = userModel.planetCode ?: ""
                })
            }
        }

        fun getRequestedMember(teamId: String, realm: Realm): List<RealmUserModel> {
            return getUsers(teamId, realm, "request")
        }

        fun getJoinedMember(teamId: String, realm: Realm): List<RealmUserModel> {
            return getUsers(teamId, realm, "membership")
        }

        fun isTeamLeader(teamId: String, userId: String, realm: Realm): Boolean {
            val count = realm.query<RealmMyTeam>("teamId == $0 AND docType == 'membership' AND userId == $1 AND isLeader == true", teamId, userId).first().find()
            return count != null
        }

        fun getUsers(teamId: String, realm: Realm, docType: String = ""): List<RealmUserModel> {
            val queryStr = if (docType.isNotEmpty()) {
                "teamId == $0 AND docType == $1"
            } else {
                "teamId == $0"
            }

            val teams = if (docType.isNotEmpty()) {
                realm.query<RealmMyTeam>(queryStr, teamId, docType).find()
            } else {
                realm.query<RealmMyTeam>(queryStr, teamId).find()
            }

            return teams.mapNotNull {
                team -> realm.query<RealmUserModel>("id == $0", team.userId).first().find()
            }.distinct()
        }

        fun filterUsers(teamId: String, user: String, realm: Realm): List<RealmUserModel> {
            val teams = realm.query<RealmMyTeam>("teamId == $0", teamId).find()

            return teams.mapNotNull { team ->
                realm.query<RealmUserModel>("id == $0", team.userId).first().find()?.takeIf {
                    it.name?.contains(user) == true
                }
            }
        }

        fun serialize(team: RealmMyTeam): JsonObject {
            val gson = Gson()
            val obj = JsonObject().apply {
                JsonUtils.addString(this, "_id", team._id)
                JsonUtils.addString(this, "_rev", team._rev)
                addProperty("name", team.name)
                addProperty("userId", team.userId)

                if (team.docType != "report") {
                    addProperty("limit", team.limit)
                    addProperty("amount", team.amount)
                    addProperty("date", team.date)
                    addProperty("public", team.isPublic)
                    addProperty("isLeader", team.isLeader)
                }

                addProperty("createdDate", team.createdDate)
                addProperty("description", team.description)
                addProperty("beginningBalance", team.beginningBalance)
                addProperty("sales", team.sales)
                addProperty("otherIncome", team.otherIncome)
                addProperty("wages", team.wages)
                addProperty("otherExpenses", team.otherExpenses)
                addProperty("startDate", team.startDate)
                addProperty("endDate", team.endDate)
                addProperty("updatedDate", team.updatedDate)
                JsonUtils.addString(this, "teamId", team.teamId)
                addProperty("teamType", team.teamType)
                addProperty("teamPlanetCode", team.teamPlanetCode)
                addProperty("docType", team.docType)
                addProperty("status", team.status)
                addProperty("userPlanetCode", team.userPlanetCode)
                addProperty("parentCode", team.parentCode)
                addProperty("type", team.type)
                addProperty("route", team.route)
                addProperty("sourcePlanet", team.sourcePlanet)
                addProperty("services", team.services)
                addProperty("createdBy", team.createdBy)
                addProperty("resourceId", team.resourceId)
                addProperty("rules", team.rules)

                if (team.teamType == "debit" || team.teamType == "credit") {
                    addProperty("type", team.teamType)
                }
            }

            return JsonParser.parseString(gson.toJson(obj)).asJsonObject
        }

        fun getMyTeamsByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyTeam> {
            val userId = settings?.getString("userId", "--") ?: "--"
            val teamIds = realm.query<RealmMyTeam>("userId == $0 AND docType == 'membership'", userId).find().map { it._id }

            return realm.query<RealmMyTeam>("_id IN $0", teamIds).find()
        }
    }

    fun requested(userId: String, realm: Realm): Boolean {
        val count = realm.query<RealmMyTeam>("docType == 'request' AND teamId == $0 AND userId == $1", _id, userId).first().find()
        return count != null
    }

    fun isMyTeam(userId: String, realm: Realm): Boolean {
        val count = realm.query<RealmMyTeam>("userId == $0 AND teamId == $1 AND docType == 'membership'", userId, _id).first().find()
        return count != null
    }

    fun leave(user: RealmUserModel, realm: Realm) {
        realm.writeBlocking {
            query<RealmMyTeam>("userId == $0 AND teamId == $1 AND docType == 'membership'", user.id, this@RealmMyTeam._id)
                .find().forEach {
                    delete(it)
                }
        }
    }
}