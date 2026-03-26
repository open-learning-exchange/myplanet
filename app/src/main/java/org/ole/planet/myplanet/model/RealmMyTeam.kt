package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.DownloadUtils.openDownloadService
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils.getUrl

open class RealmMyTeam : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var courses: RealmList<String>? = null
    @Index
    var teamId: String? = null
    var name: String? = null
    @Index
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
    @Index
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
        @JvmStatic
        fun populateTeamFields(doc: JsonObject, team: RealmMyTeam, includeCourses: Boolean = false) {
            val hadLocalChanges = team.updated

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
            if (!hadLocalChanges) {
                team.docType = JsonUtils.getString("docType", doc)
            }
            team.isPublic = JsonUtils.getBoolean("public", doc)
            team.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
            team.sales = JsonUtils.getInt("sales", doc)
            team.otherIncome = JsonUtils.getInt("otherIncome", doc)
            team.wages = JsonUtils.getInt("wages", doc)
            team.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
            team.startDate = JsonUtils.getLong("startDate", doc)
            team.endDate = JsonUtils.getLong("endDate", doc)
            team.updatedDate = JsonUtils.getLong("updatedDate", doc)

            val localCourses = team.courses?.toList() ?: emptyList()

            if (!hadLocalChanges) {
                team.updated = JsonUtils.getBoolean("updated", doc)
            }

            val coursesArray = JsonUtils.getJsonArray("courses", doc)
            val serverCourseIds = mutableListOf<String>()
            for (e in coursesArray) {
                try {
                    val id = e.asJsonObject["_id"].asString
                    serverCourseIds.add(id)
                } catch (ex: Exception) {
                    if (e.isJsonPrimitive) {
                        serverCourseIds.add(e.asString)
                    }
                }
            }

            if (hadLocalChanges) {
                val mergedCourses = serverCourseIds.toMutableSet()
                mergedCourses.addAll(localCourses)
                team.courses = RealmList()
                team.courses?.addAll(mergedCourses)
            } else {
                team.courses = RealmList()
                team.courses?.addAll(serverCourseIds)
            }
        }

        private fun processDescription(description: String?) {
            val links = extractLinks(description ?: "")
            val baseUrl = getUrl()
            val concatenatedLinks = LinkedHashSet<String>()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            openDownloadService(context, ArrayList(concatenatedLinks), true)
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
            val docType = JsonUtils.getString("docType", doc)
            val userId = JsonUtils.getString("userId", doc)
            val teamIdField = JsonUtils.getString("teamId", doc)

            if (docType == "membership" && userId.isNotBlank() && teamIdField.isNotBlank()) {
                // Server accepted the request (possibly as a new doc); remove any stale request records
                mRealm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamIdField)
                    .equalTo("userId", userId)
                    .equalTo("docType", "request")
                    .findAll()
                    .deleteAllFromRealm()
            } else if (docType == "request" && userId.isNotBlank() && teamIdField.isNotBlank()) {
                // Skip stale request record if the user is already a member
                val alreadyMember = mRealm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamIdField)
                    .equalTo("userId", userId)
                    .equalTo("docType", "membership")
                    .count() > 0
                if (alreadyMember) return
            }

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
            val teamIds = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()
                .mapNotNull { it.teamId?.takeIf { id -> id.isNotBlank() } }

            if (teamIds.isEmpty()) {
                return mutableListOf()
            }

            return realm.where(RealmMyTeam::class.java)
                .`in`("teamId", teamIds.toTypedArray())
                .equalTo("docType", "resourceLink")
                .findAll()
                .mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
                .toMutableList()
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
        fun serialize(team: RealmMyTeam): JsonObject {
            val `object` = JsonObject()

            JsonUtils.addString(`object`, "_id", team._id)
            JsonUtils.addString(`object`, "_rev", team._rev)

            if (team.docType == "resourceLink") {
                `object`.addProperty("resourceId", team.resourceId)
                `object`.addProperty("title", team.title)
                JsonUtils.addString(`object`, "teamId", team.teamId)
                `object`.addProperty("teamPlanetCode", team.teamPlanetCode)
                `object`.addProperty("teamType", team.teamType)
                `object`.addProperty("sourcePlanet", team.sourcePlanet)
                `object`.addProperty("docType", team.docType)
                return JsonParser.parseString(JsonUtils.gson.toJson(`object`)).asJsonObject
            }

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
            `object`.addProperty("title", team.title)
            `object`.addProperty("rules", team.rules)

            if (team.teamType == "debit" || team.teamType == "credit") {
                `object`.addProperty("type", team.teamType)
            }

            return JsonParser.parseString(JsonUtils.gson.toJson(`object`)).asJsonObject
        }

        @JvmStatic
        fun serialize(team: RealmMyTeam, realm: Realm): JsonObject {
            val `object` = serialize(team)

            if (!team.courses.isNullOrEmpty()) {
                val coursesArray = JsonArray()

                val courseIds = team.courses?.toTypedArray() ?: emptyArray()

                if (courseIds.isNotEmpty()) {
                    val courses = realm.where(RealmMyCourse::class.java)
                        .`in`("courseId", courseIds)
                        .findAll()

                    val courseMap = courses.associateBy { it.courseId }

                    team.courses?.forEach { courseId ->
                        val course = courseMap[courseId]
                        if (course != null) {
                            val courseJson = RealmMyCourse.serialize(course, realm)
                            coursesArray.add(courseJson)
                        }
                    }
                }
                `object`.add("courses", coursesArray)
            }
            return `object`
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

    fun isMyTeam(userID: String?, mRealm: Realm): Boolean {
        return mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", userID)
            .equalTo("teamId", _id)
            .equalTo("docType", "membership")
            .count() > 0
    }

    fun leave(user: RealmUser?, mRealm: Realm) {
        val teams = mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", user?.id)
            .equalTo("teamId", this._id)
            .equalTo("docType", "membership")
            .findAll()

        if (!teams.isEmpty()) {
            val startedTransaction = !mRealm.isInTransaction
            if (startedTransaction) {
                mRealm.beginTransaction()
            }
            try {
                teams.deleteAllFromRealm()
                if (startedTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (startedTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
        }
    }
}
