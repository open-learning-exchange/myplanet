package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray

interface ProgressRepository {
    suspend fun fetchCourseData(userId: String?): JsonArray
}
