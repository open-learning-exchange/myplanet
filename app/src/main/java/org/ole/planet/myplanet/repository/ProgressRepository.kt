package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmSubmission

interface ProgressRepository {
    suspend fun fetchCourseData(userId: String?): JsonArray
    private fun submissionMap(
        submissions: List<RealmSubmission>,
        realm: Realm,
        examIds: List<String>,
        obj: JsonObject
    )
}
