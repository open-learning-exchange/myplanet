package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmCourseStep

interface ProgressRepository {
    suspend fun getCourseProgress(userId: String?): HashMap<String?, JsonObject>
    suspend fun getCurrentProgress(steps: List<RealmCourseStep?>?, userId: String?, courseId: String?): Int
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
