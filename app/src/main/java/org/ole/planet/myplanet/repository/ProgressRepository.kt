package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    suspend fun getCourseProgress(userId: String): Flow<HashMap<String?, JsonObject>>
    suspend fun fetchCourseData(userId: String?): JsonArray
}
