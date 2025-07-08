package org.ole.planet.myplanet.data.repository

import io.realm.Case
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.domain.repository.SurveyRepository
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class SurveyRepositoryImpl(private val databaseService: DatabaseService) : SurveyRepository {
    private fun <T> useRealm(block: (Realm) -> T): T {
        val realm = databaseService.realmInstance
        return try {
            block(realm)
        } finally {
            if (!realm.isClosed) realm.close()
        }
    }

    override fun getPendingSurveys(userId: String?): List<RealmSubmission> =
        useRealm { realm ->
            val pending = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAll()

            val uniqueMap = mutableMapOf<String, RealmSubmission>()
            pending.forEach { submission ->
                val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()
                if (exam != null && !uniqueMap.containsKey(examId)) {
                    uniqueMap[examId] = submission
                }
            }
            uniqueMap.values.toList()
        }

    override fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> =
        useRealm { realm ->
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
