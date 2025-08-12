package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission

class SubmissionRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : SubmissionRepository {

    override suspend fun getPendingSurveysAsync(userId: String?): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("status", "pending")
                .equalTo("type", "survey")
                .findAll()
        }
    }

    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
        }
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .findAll()
        }
    }

    override suspend fun getSubmissionsByType(type: String): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("type", type)
                .findAll()
        }
    }

    override suspend fun saveSubmission(submission: RealmSubmission) {
        databaseService.executeTransactionAsync { realm ->
            realm.copyToRealmOrUpdate(submission)
        }
    }

    override suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { updater(it) }
        }
    }

    override suspend fun deleteSubmission(id: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.deleteFromRealm()
        }
    }

    override fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return databaseService.withRealm { realm ->
            realm.copyFromRealm(
                realm.where(RealmSubmission::class.java)
                    .equalTo("userId", userId)
                    .equalTo("status", "pending")
                    .equalTo("type", "survey")
                    .findAll()
            )
        }
    }
}
