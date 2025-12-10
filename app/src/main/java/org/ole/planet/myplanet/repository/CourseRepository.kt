package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

interface CourseRepository {
    suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>>
    suspend fun getCoursesByTeam(teamId: String?): List<RealmMyCourse>
    suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep>
    suspend fun markCourseAdded(courseId: String, userId: String?): Boolean
}
