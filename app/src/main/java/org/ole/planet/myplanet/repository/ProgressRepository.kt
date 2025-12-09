package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.ChallengeCounts

interface ProgressRepository {
    suspend fun fetchCourseData(userId: String?): JsonArray
    suspend fun getChallengeCounts(userId: String?, startTime: Long, endTime: Long, courseId: String): ChallengeCounts
}
