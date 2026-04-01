package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag

interface CoursesRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    suspend fun getAllCourses(orderBy: String, sort: io.realm.Sort): List<RealmMyCourse>
    fun getAllCourses(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse>
    fun getMyCourseByUserId(userId: String?, libs: List<RealmMyCourse>?): List<RealmMyCourse>
    fun getOurCourse(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse>
    fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse>
    suspend fun getMyCourses(userId: String): List<RealmMyCourse>
    suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>>
    suspend fun getCourseById(courseId: String): RealmMyCourse?
    suspend fun getCourseByCourseId(courseId: String): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseIds: List<String>): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String): List<RealmCourseStep>
    suspend fun getCourseStepIds(courseId: String): List<String?>
    suspend fun markCourseAdded(courseId: String, userId: String?): Result<Boolean>
    suspend fun markCoursesAdded(courseIds: List<String>, userId: String?): Result<Boolean>
    suspend fun joinCourse(courseId: String, userId: String): Result<Unit>
    suspend fun leaveCourse(courseId: String, userId: String): Result<Unit>
    suspend fun isMyCourse(userId: String?, courseId: String?): Boolean
    suspend fun search(query: String): List<RealmMyCourse>
    suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<RealmMyCourse>
    suspend fun saveSearchActivity(
        searchText: String,
        userName: String,
        planetCode: String,
        parentCode: String,
        tags: List<org.ole.planet.myplanet.model.RealmTag>,
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
    suspend fun getCurrentProgress(steps: List<RealmCourseStep?>?, userId: String?, courseId: String?): Int
    suspend fun getCourseProgress(userId: String?): java.util.HashMap<String?, com.google.gson.JsonObject>
    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean
    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean
    suspend fun getCourseTags(courseId: String): List<RealmTag>
    suspend fun getCourseRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject>
    suspend fun deleteCourseProgress(courseId: String?)
    suspend fun filterCoursesByTag(query: String, tags: List<RealmTag>, isMyCourseLib: Boolean, userId: String?): List<RealmMyCourse>
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
}
