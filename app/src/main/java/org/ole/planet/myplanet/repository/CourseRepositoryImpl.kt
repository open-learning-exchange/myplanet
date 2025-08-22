package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java)
    }

    override suspend fun getCourseById(id: String): RealmMyCourse? {
        return findByField(RealmMyCourse::class.java, "courseId", id)
    }

    override suspend fun getEnrolledCourses(userId: String): List<RealmMyCourse> =
        getCoursesByUserId(userId)

    override suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse> {
        return queryList(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun saveCourse(course: RealmMyCourse) {
        save(course)
    }

    override suspend fun updateCourse(id: String, updater: (RealmMyCourse) -> Unit) {
        update(RealmMyCourse::class.java, "courseId", id, updater)
    }

    override suspend fun deleteCourse(id: String) {
        delete(RealmMyCourse::class.java, "courseId", id)
    }

    override suspend fun updateMyCourseFlag(courseId: String, isMyCourse: Boolean) {
        update(RealmMyCourse::class.java, "courseId", courseId) { it.isMyCourse = isMyCourse }
    }

    override suspend fun updateMyCourseFlag(courseIds: List<String>, isMyCourse: Boolean) {
        executeTransaction { realm ->
            courseIds.forEach { id ->
                realm.where(RealmMyCourse::class.java)
                    .equalTo("courseId", id)
                    .findFirst()
                    ?.let { it.isMyCourse = isMyCourse }
            }
        }
    }
}
