package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

interface CoursesRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    fun getMyCourses(userId: String?, courses: List<RealmMyCourse>): List<RealmMyCourse>
    suspend fun getMyCourses(userId: String): List<RealmMyCourse>
    suspend fun getOurCourses(userId: String): List<RealmMyCourse>
    suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>>
    suspend fun getCourseById(courseId: String): RealmMyCourse?
    suspend fun getCourseByCourseId(courseId: String): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseIds: List<String>): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String): List<RealmCourseStep>
    suspend fun getCourseStepIds(courseId: String): List<String?>
    suspend fun markCourseAdded(courseId: String, userId: String?): Boolean
    suspend fun joinCourse(courseId: String, userId: String)
    suspend fun leaveCourse(courseId: String, userId: String)
    suspend fun isMyCourse(userId: String?, courseId: String?): Boolean
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
    suspend fun deleteCourseProgress(courseId: String)
    suspend fun getMyCourseIds(userId: String): JsonArray
    suspend fun removeCourseFromShelf(courseId: String, userId: String)
    suspend fun getAllCourses(userId: String?): List<RealmMyCourse>
    suspend fun getCoursesByIds(ids: List<String>): List<RealmMyCourse>
}
