package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress

class CourseProgressRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseProgressRepository {

    override suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress> {
        val progressList = queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
        }
        return progressList.associate { (it.courseId ?: "") to it }
    }
}
