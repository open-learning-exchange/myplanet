package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyCourse

interface CourseRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    suspend fun getCourseById(id: String): RealmMyCourse?
    suspend fun getEnrolledCourses(): List<RealmMyCourse>
    suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse>
    suspend fun saveCourse(course: RealmMyCourse)
    suspend fun updateCourse(id: String, updater: (RealmMyCourse) -> Unit)
    suspend fun deleteCourse(id: String)
    @Deprecated("Use async version", ReplaceWith("getAllCourses()"))
    fun getAllCoursesSync(): List<RealmMyCourse>
    @Deprecated("Use async version", ReplaceWith("getCourseById(id)"))
    fun getCourseByIdSync(id: String): RealmMyCourse?
    @Deprecated("Use async version", ReplaceWith("getEnrolledCourses()"))
    fun getEnrolledCoursesSync(): List<RealmMyCourse>
}
