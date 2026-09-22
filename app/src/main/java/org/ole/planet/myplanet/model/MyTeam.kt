package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.FileUtils.getOlePath
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Entity(tableName = "teams", indices = [Index("_id"), Index("teamId"), Index("userId"), Index("type"), Index("docType")])
open class MyTeam(
    @PrimaryKey @JvmField var _id: String = "",
    @JvmField var _rev: String? = null,
    var courses: List<String>? = null,
    var teamId: String? = null,
    var name: String? = null,
    var userId: String? = null,
    var description: String? = null,
    var requests: String? = null,
    var sourcePlanet: String? = null,
    var limit: Int = 0,
    var createdDate: Long = 0,
    var resourceId: String? = null,
    var status: String? = null,
    var teamType: String? = null,
    var teamPlanetCode: String? = null,
    var userPlanetCode: String? = null,
    var parentCode: String? = null,
    var docType: String? = null,
    var title: String? = null,
    var route: String? = null,
    var services: String? = null,
    var createdBy: String? = null,
    var rules: String? = null,
    var isLeader: Boolean = false,
    var type: String? = null,
    var amount: Int = 0,
    var date: Long = 0,
    var isPublic: Boolean = false,
    @ColumnInfo(name = "isUpdated") var updated: Boolean = false,
    var isDeletePending: Boolean = false,
    var beginningBalance: Int = 0,
    var sales: Int = 0,
    var otherIncome: Int = 0,
    var wages: Int = 0,
    var otherExpenses: Int = 0,
    var startDate: Long = 0,
    var endDate: Long = 0,
    var updatedDate: Long = 0,
    var imageName: String? = null
) {
    @get:androidx.room.Ignore
    var id: String
        get() = _id
        set(value) { _id = value }

    companion object {
        fun getFirstAttachmentName(doc: JsonObject): String? {
            val attachments = doc.getAsJsonObject("_attachments") ?: return null
            return attachments.keySet().firstOrNull()
        }

        fun getAttachmentFile(context: Context, teamId: String?, imageName: String?): File? {
            if (teamId.isNullOrBlank() || imageName.isNullOrBlank()) return null
            return File(
                "${getOlePath(context)}team_attachments/$teamId/$imageName"
            )
        }

        fun populateTeamFields(doc: JsonObject, team: MyTeam, includeCourses: Boolean = false) {
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
            getFirstAttachmentName(doc)?.let { team.imageName = it }

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
                team.courses = mergedCourses.toList()
            } else {
                team.courses = serverCourseIds.toList()
            }
        }

        fun populateReportFields(doc: JsonObject, team: MyTeam) {
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
            getFirstAttachmentName(doc)?.let { team.imageName = it }
        }

        fun serialize(team: MyTeam): JsonObject {
            if (team.isDeletePending) {
                return buildJsonObject {
                    if (!team._id.isNullOrEmpty()) put("_id", team._id)
                    if (!team._rev.isNullOrEmpty()) put("_rev", team._rev)
                    put("_deleted", true)
                }.toGson()
            }

            if (team.docType == "resourceLink") {
                val `object` = buildJsonObject {
                    if (!team._id.isNullOrEmpty()) put("_id", team._id)
                    if (!team._rev.isNullOrEmpty()) put("_rev", team._rev)
                    put("resourceId", team.resourceId)
                    put("title", team.title)
                    if (!team.teamId.isNullOrEmpty()) put("teamId", team.teamId)
                    put("teamPlanetCode", team.teamPlanetCode)
                    put("teamType", team.teamType)
                    put("sourcePlanet", team.sourcePlanet)
                    put("docType", team.docType)
                }.toGson()

                val keysToRemove = `object`.keySet().filter { `object`.get(it).isJsonNull }
                keysToRemove.forEach { `object`.remove(it) }
                return `object`
            }

            val `object` = buildJsonObject {
                if (!team._id.isNullOrEmpty()) put("_id", team._id)
                if (!team._rev.isNullOrEmpty()) put("_rev", team._rev)
                put("name", team.name)
                put("userId", team.userId)
                if (team.docType != "report" && team.docType != "request") {
                    put("limit", team.limit)
                    put("amount", team.amount)
                    put("date", team.date)
                    put("public", team.isPublic)
                    put("isLeader", team.isLeader)
                }
                if (team.docType != "request") {
                    put("createdDate", team.createdDate)
                    put("description", team.description)
                    put("beginningBalance", team.beginningBalance)
                    put("sales", team.sales)
                    put("otherIncome", team.otherIncome)
                    put("wages", team.wages)
                    put("otherExpenses", team.otherExpenses)
                    put("startDate", team.startDate)
                    put("endDate", team.endDate)
                    put("updatedDate", team.updatedDate)
                }
                if (!team.teamId.isNullOrEmpty()) put("teamId", team.teamId)
                put("teamType", team.teamType)
                put("teamPlanetCode", team.teamPlanetCode)
                put("docType", team.docType)
                put("status", team.status)
                put("userPlanetCode", team.userPlanetCode)
                put("parentCode", team.parentCode)
                put("type", team.type)
                put("route", team.route)
                put("sourcePlanet", team.sourcePlanet)
                put("services", team.services)
                put("createdBy", team.createdBy)
                put("resourceId", team.resourceId)
                put("title", team.title)
                put("rules", team.rules)

                if (team.teamType == "debit" || team.teamType == "credit") {
                    put("type", team.teamType)
                }
            }.toGson()

            val keysToRemove = `object`.keySet().filter { `object`.get(it).isJsonNull }
            keysToRemove.forEach { `object`.remove(it) }
            return `object`
        }

        fun serialize(team: MyTeam, courses: List<MyCourse>, coursesResourcesMap: Map<String, Map<String?, List<MyLibrary>>>): JsonObject {
            val `object` = serialize(team)

            if (!team.courses.isNullOrEmpty()) {
                val coursesArray = JsonArray()

                val courseMap = courses.associateBy { it.courseId }

                team.courses?.forEach { courseId ->
                    val course = courseMap[courseId]
                    if (course != null) {
                        val courseResources = coursesResourcesMap[courseId] ?: emptyMap()
                        val courseJson = MyCourse.serialize(course, courseResources)
                        coursesArray.add(courseJson)
                    }
                }
                `object`.add("courses", coursesArray)
            }
            return `object`
        }
    }
}
