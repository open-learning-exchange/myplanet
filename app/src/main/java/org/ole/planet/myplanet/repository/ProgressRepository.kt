package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseProgressState
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.utils.JsonUtils

private val stepMistakeMapType = object : TypeToken<Map<String, Int>>() {}.type

interface ProgressRepository {
    suspend fun getCourseProgress(courseIds: List<String>, userId: String?): Map<String, CourseProgressState>
    suspend fun getCurrentProgress(steps: List<CourseStep?>?, userId: String?, courseId: String?): Int
    suspend fun fetchCourseData(userId: String?): JsonArray
    suspend fun getCourseProgressRows(userId: String?): List<CoursesProgressRow> {
        val jsonArray = fetchCourseData(userId)
        return jsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject

            val courseId = if (obj.has("courseId") && !obj.get("courseId").isJsonNull) obj.get("courseId").asString else null
            val courseName = if (obj.has("courseName") && !obj.get("courseName").isJsonNull) obj.get("courseName").asString else null
            if (courseId == null || courseName == null) return@mapNotNull null

            val progressObj = obj.get("progress")?.takeIf { it.isJsonObject }?.asJsonObject
            val progressCurrent = progressObj?.get("current")?.takeIf { !it.isJsonNull }?.asInt
            val progressMax = progressObj?.get("max")?.takeIf { !it.isJsonNull }?.asInt

            val mistakes = obj.get("mistakes")?.takeIf { !it.isJsonNull }?.asInt

            val stepMistakeElem = obj.get("stepMistake")
            val stepMistake: Map<String, Int>? = if (stepMistakeElem != null && !stepMistakeElem.isJsonNull) {
                JsonUtils.gson.fromJson(stepMistakeElem, stepMistakeMapType)
            } else {
                null
            }

            CoursesProgressRow(
                courseId = courseId,
                courseName = courseName,
                progressCurrent = progressCurrent,
                progressMax = progressMax,
                mistakes = mistakes,
                stepMistake = stepMistake
            )
        }
    }
    suspend fun getProgressRecords(userId: String?): List<CourseProgress>
    suspend fun getCompletedCourses(userId: String): List<CourseCompletion>
    suspend fun saveCourseProgress(
        userId: String?,
        planetCode: String?,
        parentCode: String?,
        courseId: String?,
        stepNum: Int,
        passed: Boolean?
    )
    suspend fun hasUserCompletedSync(userId: String): Boolean
    suspend fun insertCourseProgressFromSync(docs: List<JsonObject>)
    fun findProgressForCourse(courseData: JsonArray, courseId: String): JsonObject?
    suspend fun getPendingCourseProgressUploads(): List<CourseProgress>
    suspend fun markCourseProgressUploaded(localId: String, remoteId: String, rev: String): Boolean
}
