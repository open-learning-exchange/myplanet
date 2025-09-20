package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyCourse

interface CourseRepository {
    suspend fun getAllCourses(): List<RealmMyCourse>
    suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean)
    suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        courseIds.forEach { updateMyCourseFlag(it, isMyCourse) }
    }
}
