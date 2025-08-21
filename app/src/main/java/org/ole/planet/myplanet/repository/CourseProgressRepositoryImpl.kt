package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmCourseProgress

class CourseProgressRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : CourseProgressRepository {

    override suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress> {
        return databaseService.withRealmAsync { realm ->
            val progressList = realm.queryList(RealmCourseProgress::class.java) {
                equalTo("userId", userId)
            }
            progressList.associate { (it.courseId ?: "") to it }
        }
    }
}
