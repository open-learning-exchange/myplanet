package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class SubmissionRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : SubmissionRepository {

    override suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmSubmission::class.java) {
                equalTo("userId", userId)
                equalTo("status", "pending")
                equalTo("type", "survey")
            }
        }
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        return databaseService.withRealmAsync { realm ->
            val titles = mutableListOf<String>()
            submissions.forEach { submission ->
                val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()
                exam?.name?.let { titles.add(it) }
            }
            titles
        }
    }

    override suspend fun getSubmissionById(id: String): RealmSubmission? {
        return databaseService.withRealmAsync { realm ->
            realm.findCopyByField(RealmSubmission::class.java, "id", id)
        }
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmSubmission::class.java) {
                equalTo("userId", userId)
            }
        }
    }

    override suspend fun getSubmissionsByType(type: String): List<RealmSubmission> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmSubmission::class.java) {
                equalTo("type", type)
            }
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

}
