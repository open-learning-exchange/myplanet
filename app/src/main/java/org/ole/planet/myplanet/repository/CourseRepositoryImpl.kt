package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse

class CourseRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val userRepository: UserRepository,
) : CourseRepository {

    override suspend fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.copyFromRealm(
                realm.where(RealmMyCourse::class.java).findAll()
            )
        }
    }

    override suspend fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getEnrolledCourses(): List<RealmMyCourse> {
        val userId = userRepository.getCurrentUser()?.id ?: ""
        return databaseService.withRealmAsync { realm ->
            realm.copyFromRealm(
                realm.where(RealmMyCourse::class.java)
                    .equalTo("userId", userId)
                    .findAll()
            )
        }
    }

    override suspend fun getCoursesByUserId(userId: String): List<RealmMyCourse> {
        return databaseService.withRealmAsync { realm ->
            realm.copyFromRealm(
                realm.where(RealmMyCourse::class.java)
                    .equalTo("userId", userId)
                    .findAll()
            )
        }
    }

    override suspend fun saveCourse(course: RealmMyCourse) {
        databaseService.executeTransactionAsync { realm ->
            realm.copyToRealmOrUpdate(course)
        }
    }

    override suspend fun updateCourse(id: String, updater: (RealmMyCourse) -> Unit) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.let { updater(it) }
        }
    }

    override suspend fun deleteCourse(id: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.deleteFromRealm()
        }
    }
}
