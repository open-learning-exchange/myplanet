package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class SubmissionRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), SubmissionRepository {

    override suspend fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("status", "pending")
            equalTo("type", "survey")
        }
    }

    override suspend fun getSurveyCountByUser(userId: String?): Int {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
            equalTo("type", "survey")
            equalTo("status", "pending")
        }.size
    }

    override suspend fun getSurveyTitlesFromSubmissions(
        submissions: List<RealmSubmission>
    ): List<String> {
        return withRealm { realm ->
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
        return findByField(RealmSubmission::class.java, "id", id)
    }

    override suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getSubmissionsByType(type: String): List<RealmSubmission> {
        return queryList(RealmSubmission::class.java) {
            equalTo("type", type)
        }
    }

    override suspend fun saveSubmission(submission: RealmSubmission) {
        save(submission)
    }

    override suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit) {
        update(RealmSubmission::class.java, "id", id, updater)
    }

    override suspend fun deleteSubmission(id: String) {
        delete(RealmSubmission::class.java, "id", id)
    }

}
