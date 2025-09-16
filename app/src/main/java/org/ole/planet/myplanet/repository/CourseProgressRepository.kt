package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmCourseProgress

interface CourseProgressRepository {
    suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress>
    suspend fun saveProgress(courseId: String, userId: String?, stepNum: Int, passed: Boolean)
}
