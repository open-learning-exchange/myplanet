package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress

class CourseProgressRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseProgressRepository {

    override suspend fun getCourseProgress(userId: String?): Map<String?, JsonObject> {
        return withRealm { realm ->
            RealmCourseProgress.getCourseProgress(realm, userId)
        }
    }
}
