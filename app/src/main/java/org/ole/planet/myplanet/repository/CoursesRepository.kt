package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.TagEntity

interface CoursesRepository {
    suspend fun getAllCourses(): List<MyCourse>
    fun getMyCourses(userId: String?, courses: List<MyCourse>): List<MyCourse>
    suspend fun getMyCourses(userId: String): List<MyCourse>
    suspend fun getMyCoursesFlow(userId: String): Flow<List<MyCourse>>
    suspend fun getCourseById(courseId: String): MyCourse?
    fun getCourseByCourseIdFlow(courseId: String): Flow<MyCourse?>
    suspend fun getCoursesByIds(courseIds: List<String>): List<MyCourse>
    suspend fun getCourseOnlineResources(courseId: String?): List<MyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<MyLibrary>
    suspend fun getCourseOfflineResources(courseIds: List<String>): List<MyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String): List<CourseStep>
    suspend fun batchInsertMyCourses(shelfId: String?, documents: List<JsonObject>): Int
    suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean>
    suspend fun joinCourse(courseId: String, userId: String): Result<Unit>
    suspend fun leaveCourse(courseId: String, userId: String): Result<Unit>
    suspend fun isMyCourse(userId: String?, courseId: String?): Boolean
    suspend fun search(query: String): List<MyCourse>
    suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<MyCourse>
    suspend fun saveSearchActivity(
        searchText: String,
        userName: String,
        planetCode: String,
        parentCode: String,
        tags: List<TagEntity>,
        grade: String,
        subject: String
    )
    suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgressData?
    suspend fun getCourseTitleById(courseId: String): String?
    suspend fun isCourseCertified(courseId: String): Boolean
    suspend fun updateCourseProgress(courseId: String?, stepNum: Int, passed: Boolean)
    suspend fun getCourseStepData(stepId: String, userId: String?): CourseStepData
    suspend fun getMyCourseIds(userId: String): JsonArray
    suspend fun removeCourseFromShelf(courseId: String, userId: String)
    suspend fun logCourseVisit(courseId: String, title: String, userId: String)
    suspend fun getCurrentProgress(steps: List<CourseStep?>?, userId: String?, courseId: String?): Int
    suspend fun getCourseProgress(userId: String?, courseIds: List<String>): HashMap<String?, JsonObject>
    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean
    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean
    suspend fun getCourseTagsBulk(courseIds: List<String>): Map<String, List<TagEntity>>
    suspend fun getCourseRatings(userId: String?): HashMap<String?, JsonObject>
    suspend fun deleteCourseProgress(courseId: String?)
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun flushPendingCourseResources()
    suspend fun insertCertificationsFromSync(jsonArray: JsonArray)
}
