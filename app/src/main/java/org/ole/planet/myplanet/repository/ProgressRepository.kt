package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface ProgressRepository {
    suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject>
    suspend fun fetchCourseData(userId: String?): JsonArray
    suspend fun saveCourseProgress(
        userId: String?,
        planetCode: String?,
        parentCode: String?,
        courseId: String?,
        stepNum: Int,
        passed: Boolean?
    )

    suspend fun getCurrentProgress(courseId: String?, userId: String?): Int
}
