package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

interface SubmissionRepository {
    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getSubmissionCountByUser(userId: String?): Int
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission>
    suspend fun getSubmissionsByType(type: String): List<RealmSubmission>
    suspend fun getExamMapForSubmissions(submissions: List<RealmSubmission>): Map<String?, RealmStepExam>
    suspend fun hasPendingSubmissions(): Boolean
    suspend fun saveSubmission(submission: RealmSubmission)
    suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit)
    suspend fun deleteSubmission(id: String)
}
