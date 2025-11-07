package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

interface CourseRepository {
    suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse?
    suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary>
    suspend fun getCourseExamCount(courseId: String?): Int
    suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep>
    suspend fun markCourseAdded(courseId: String, userId: String?): Boolean
    suspend fun getAllCourses(): List<RealmMyCourse?>
    suspend fun getRatings(userId: String?): HashMap<String?, JsonObject>
    suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject>
    suspend fun getCourseResources(courseIds: List<String?>): List<RealmMyLibrary>
}
