package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseStep

interface ProgressRepository {
    suspend fun getCourseProgress(courseIds: List<String>, userId: String?): HashMap<String?, JsonObject>
    suspend fun getCurrentProgress(steps: List<CourseStep?>?, userId: String?, courseId: String?): Int
    suspend fun fetchCourseData(userId: String?): JsonArray
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
}
