package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface CourseProgressRepository {
    suspend fun getCourseProgress(userId: String?): Map<String?, JsonObject>
}
