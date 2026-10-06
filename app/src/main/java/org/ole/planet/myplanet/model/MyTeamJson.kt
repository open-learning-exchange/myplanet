package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for MyTeam, kept out of the Room entity.

fun MyTeam.Companion.getFirstAttachmentName(doc: JsonObject): String? {
    val attachments = doc.getAsJsonObject("_attachments") ?: return null
    return attachments.keySet().firstOrNull()
}

fun MyTeam.Companion.populateTeamFields(doc: JsonObject, team: MyTeam, includeCourses: Boolean = false) {
    val kDoc = doc.toKotlinx().jsonObject
    val hadLocalChanges = team.updated

    team.userId = JsonUtils.getString("userId", kDoc)
    team.teamId = JsonUtils.getString("teamId", kDoc)
    team._rev = JsonUtils.getString("_rev", kDoc)
    team.name = JsonUtils.getString("name", kDoc)
    team.sourcePlanet = JsonUtils.getString("sourcePlanet", kDoc)
    team.title = JsonUtils.getString("title", kDoc)
    team.description = JsonUtils.getString("description", kDoc)
    team.limit = JsonUtils.getInt("limit", kDoc)
    team.status = JsonUtils.getString("status", kDoc)
    team.teamPlanetCode = JsonUtils.getString("teamPlanetCode", kDoc)
    team.createdDate = JsonUtils.getLong("createdDate", kDoc)
    team.resourceId = JsonUtils.getString("resourceId", kDoc)
    team.teamType = JsonUtils.getString("teamType", kDoc)
    team.route = JsonUtils.getString("route", kDoc)
    team.type = JsonUtils.getString("type", kDoc)
    team.services = JsonUtils.getString("services", kDoc)
    team.rules = JsonUtils.getString("rules", kDoc)
    team.parentCode = JsonUtils.getString("parentCode", kDoc)
    team.createdBy = JsonUtils.getString("createdBy", kDoc)
    team.userPlanetCode = JsonUtils.getString("userPlanetCode", kDoc)
    team.isLeader = JsonUtils.getBoolean("isLeader", kDoc)
    team.amount = JsonUtils.getInt("amount", kDoc)
    team.date = JsonUtils.getLong("date", kDoc)
    if (!hadLocalChanges) {
        team.docType = JsonUtils.getString("docType", kDoc)
    }
    team.isPublic = JsonUtils.getBoolean("public", kDoc)
    team.beginningBalance = JsonUtils.getInt("beginningBalance", kDoc)
    team.sales = JsonUtils.getInt("sales", kDoc)
    team.otherIncome = JsonUtils.getInt("otherIncome", kDoc)
    team.wages = JsonUtils.getInt("wages", kDoc)
    team.otherExpenses = JsonUtils.getInt("otherExpenses", kDoc)
    team.startDate = JsonUtils.getLong("startDate", kDoc)
    team.endDate = JsonUtils.getLong("endDate", kDoc)
    team.updatedDate = JsonUtils.getLong("updatedDate", kDoc)
    getFirstAttachmentName(doc)?.let { team.imageName = it }

    val localCourses = team.courses?.toList() ?: emptyList()

    if (!hadLocalChanges) {
        team.updated = JsonUtils.getBoolean("updated", kDoc)
    }

    val coursesArray = JsonUtils.getJsonArray("courses", kDoc)
    val serverCourseIds = mutableListOf<String>()
    for (e in coursesArray) {
        try {
            val id = (e.jsonObject["_id"] as JsonPrimitive).content
            serverCourseIds.add(id)
        } catch (ex: Exception) {
            if (e is JsonPrimitive) {
                serverCourseIds.add(e.content)
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

fun MyTeam.Companion.serialize(team: MyTeam): JsonObject {
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

fun MyTeam.Companion.serialize(team: MyTeam, courses: List<MyCourse>, coursesResourcesMap: Map<String, Map<String?, List<MyLibrary>>>): JsonObject {
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
