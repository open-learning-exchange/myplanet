package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyCourse

interface CourseRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    suspend fun getCourseById(id: String): RealmMyCourse?
    suspend fun getEnrolledCourses(userId: String): List<RealmMyCourse> =
        getCoursesByUserId(userId)
    suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse>
    suspend fun saveCourse(course: RealmMyCourse)
    suspend fun updateCourse(id: String, updater: (RealmMyCourse) -> Unit)
    suspend fun deleteCourse(id: String)
    suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean)
    suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        courseIds.forEach { updateMyCourseFlag(it, isMyCourse) }
    }

    suspend fun saveSearchActivity(activity: org.ole.planet.myplanet.model.RealmSearchActivity)
}
