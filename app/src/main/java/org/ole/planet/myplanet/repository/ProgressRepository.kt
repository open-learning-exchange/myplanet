package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface ProgressRepository {
    suspend fun fetchCourseData(userId: String?): JsonArray
    suspend fun getCourseProgress(userId: String): HashMap<String?, JsonObject>
}
