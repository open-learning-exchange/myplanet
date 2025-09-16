package org.ole.planet.myplanet.repository

import java.util.UUID
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

    override suspend fun saveProgress(
        courseId: String,
        userId: String?,
        stepNum: Int,
        passed: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        executeTransaction { realm ->
            val query = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNum)

            if (userId != null) {
                query.equalTo("userId", userId)
            } else {
                query.isNull("userId")
            }

            val progress = query.findFirst()
                ?: realm.createObject(RealmCourseProgress::class.java, UUID.randomUUID().toString()).apply {
                    createdDate = timestamp
                }

            progress.courseId = courseId
            progress.userId = userId
            progress.stepNum = stepNum
            progress.passed = passed
            progress.updatedDate = timestamp
        }
    }
}
