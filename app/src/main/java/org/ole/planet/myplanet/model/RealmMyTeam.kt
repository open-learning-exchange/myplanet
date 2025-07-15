package org.ole.planet.myplanet.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.CsvUtils
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import org.ole.planet.myplanet.utilities.Utilities.openDownloadService

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

        @JvmStatic
        fun insertMyTeams(doc: JsonObject, mRealm: Realm) {
            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            myTeams?.let { populateTeamFields(it, doc) }
            teamDataList.add(createTeamCsvRow(doc))
        }

        fun teamWriteCsv() {
            CsvUtils.writeCsv(
                "${context.getExternalFilesDir(null)}/ole/team.csv",
                arrayOf(
                    "userId",
                    "teamId",
                    "teamId_rev",
                    "name",
                    "sourcePlanet",
                    "title",
                    "description",
                    "limit",
                    "status",
                    "teamPlanetCode",
                    "createdDate",
                    "resourceId",
                    "teamType",
                    "route",
                    "type",
                    "services",
                    "rules",
                    "parentCode",
                    "createdBy",
                    "userPlanetCode",
                    "isLeader",
                    "amount",
                    "date",
                    "docType",
                    "public",
                    "beginningBalance",
                    "sales",
                    "otherIncome",
                    "wages",
                    "otherExpenses",
                    "startDate",
                    "endDate",
                    "updatedDate",
                    "courses"
                ),
                teamDataList
            )
        }

        private fun populateTeamFields(team: RealmMyTeam, doc: JsonObject) {
            team.userId = JsonUtils.getString("userId", doc)
            team.teamId = JsonUtils.getString("teamId", doc)
            team._rev = JsonUtils.getString("_rev", doc)
            team.name = JsonUtils.getString("name", doc)
            team.sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
            team.title = JsonUtils.getString("title", doc)
            team.description = JsonUtils.getString("description", doc)
            addDownloadLinks(team.description)
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
            populateCourses(team, JsonUtils.getJsonArray("courses", doc))
        }

        private fun addDownloadLinks(description: String?) {
            val links = extractLinks(description)
            val baseUrl = getUrl()
            for (link in links) {
                concatenatedLinks.add("$baseUrl/$link")
            }
            openDownloadService(context, concatenatedLinks, true)
        }

        private fun populateCourses(team: RealmMyTeam, coursesArray: JsonArray) {
            team.courses = RealmList()
            for (e in coursesArray) {
                val id = e.asJsonObject["_id"].asString
                if (!team.courses!!.contains(id)) {
                    team.courses!!.add(id)
                }
            }
        }

        private fun createTeamCsvRow(doc: JsonObject): Array<String> {
            return arrayOf(
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
        }

        private fun populateReportFields(team: RealmMyTeam, doc: JsonObject) {
            team.teamId = JsonUtils.getString("teamId", doc)
            team.description = JsonUtils.getString("description", doc)
            team.teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
            team.createdDate = JsonUtils.getLong("createdDate", doc)
            team.teamType = JsonUtils.getString("teamType", doc)
            team.docType = JsonUtils.getString("docType", doc)
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

        private fun createReportCsvRow(doc: JsonObject): Array<String> {
            return arrayOf(
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
        }

        @JvmStatic
        fun insertReports(doc: JsonObject, mRealm: Realm) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            myTeams?.let { populateReportFields(it, doc) }
            mRealm.commitTransaction()

            reportsDataList.add(createReportCsvRow(doc))
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
        fun requestToJoin(teamId: String?, userModel: RealmUserModel?, mRealm: Realm, teamType: String?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val team = mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
            team.docType = "request"
            team.createdDate = Date().time
            team.teamType = teamType
            team.userId = userModel?.id
            team.teamId = teamId
            team.updated = true
            team.teamPlanetCode = userModel?.planetCode
            team.userPlanetCode = userModel?.planetCode
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun syncTeamActivities(context: Context) {
            val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updateUrl = "${settings.getString("serverURL", "")}"
            val serverUrlMapper = ServerUrlMapper()
            val mapping = serverUrlMapper.processUrl(updateUrl)

            CoroutineScope(Dispatchers.IO).launch {
                val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
                val alternativeAvailable =
                    mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

                if (!primaryAvailable && alternativeAvailable) {
                    mapping.alternativeUrl.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = settings.edit()
                        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                    }
                }

                withContext(Dispatchers.Main) {
                    uploadTeamActivities(context)
                }
            }
        }

        private fun uploadTeamActivities(context: Context) {
            MainApplication.applicationScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        UploadManager.instance?.uploadTeams()
                    }
                    withContext(Dispatchers.IO) {
                        val apiInterface = client?.create(ApiInterface::class.java)
                        val realm = DatabaseService(context).realmInstance
                        realm.executeTransaction { transactionRealm ->
                            UploadManager.instance?.uploadTeamActivities(transactionRealm, apiInterface)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
