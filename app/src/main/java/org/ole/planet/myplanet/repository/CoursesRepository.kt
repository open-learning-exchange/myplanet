package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

interface CoursesRepository {
    suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>>
    suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse?
    suspend fun getDetachedCourseById(courseId: String?): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseIds: List<String>): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep>
    suspend fun markCourseAdded(courseId: String, userId: String?): Boolean
    suspend fun joinCourse(courseId: String, userId: String)
    suspend fun leaveCourse(courseId: String, userId: String)
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
}
