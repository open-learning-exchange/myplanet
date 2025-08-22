package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmSubmission

interface SubmissionRepository {
    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getSurveyCountByUser(userId: String?): Int
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission>
    suspend fun getSubmissionsByType(type: String): List<RealmSubmission>
    suspend fun saveSubmission(submission: RealmSubmission)
    suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit)
    suspend fun deleteSubmission(id: String)
}
