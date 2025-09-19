package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java)
    }

    override suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean) {
        update(RealmMyCourse::class.java, "courseId", courseId) { it.isMyCourse = isMyCourse }
    }

    override suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        executeTransaction { realm ->
            realm.where(RealmMyCourse::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .findAll()
                .forEach { it.isMyCourse = isMyCourse }
        }
    }
}
