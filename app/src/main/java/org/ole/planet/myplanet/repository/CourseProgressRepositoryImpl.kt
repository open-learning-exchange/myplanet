package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress

class CourseProgressRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : CourseProgressRepository {

    override suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress> {
        return databaseService.withRealmAsync { realm ->
            val progressList = realm.where(RealmCourseProgress::class.java)
                .equalTo("userId", userId)
                .findAll()
            val detachedList = realm.copyFromRealm(progressList)

            detachedList.associate { (it.courseId ?: "") to it }
        }
    }
}
