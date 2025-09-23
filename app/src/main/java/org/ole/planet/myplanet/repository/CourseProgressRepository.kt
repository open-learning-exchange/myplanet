package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmCourseProgress

interface CourseProgressRepository {
    suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress>

    fun observeCourseProgress(userId: String?): Flow<Map<String, RealmCourseProgress>>
}
