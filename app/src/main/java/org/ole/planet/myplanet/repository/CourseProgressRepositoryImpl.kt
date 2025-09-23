package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress

class CourseProgressRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseProgressRepository {

    override suspend fun getCourseProgress(userId: String?): Map<String, RealmCourseProgress> {
        if (userId.isNullOrEmpty()) {
            return emptyMap()
        }
        val progressList = queryList(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
        }
        return progressList
            .mapNotNull { progress ->
                val courseId = progress.courseId
                courseId?.let { it to progress }
            }
            .toMap()
    }

    override fun observeCourseProgress(userId: String?): Flow<Map<String, RealmCourseProgress>> {
        if (userId.isNullOrEmpty()) {
            return flowOf(emptyMap())
        }
        return queryListFlow(RealmCourseProgress::class.java) {
            equalTo("userId", userId)
        }.map { progressList ->
            progressList
                .mapNotNull { progress ->
                    val courseId = progress.courseId
                    courseId?.let { it to progress }
                }
                .toMap()
        }
    }
}
