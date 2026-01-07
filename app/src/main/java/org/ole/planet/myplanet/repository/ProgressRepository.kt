package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray

interface ProgressRepository {
    suspend fun fetchCourseData(userId: String?): JsonArray
    suspend fun saveCourseProgress(
        userId: String?,
        planetCode: String?,
        parentCode: String?,
        courseId: String?,
        stepNum: Int,
        passed: Boolean?
    )
}
