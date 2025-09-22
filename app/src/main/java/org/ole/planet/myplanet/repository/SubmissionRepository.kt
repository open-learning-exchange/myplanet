package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

interface SubmissionRepository {
    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getSubmissionCountByUser(userId: String?): Int
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission>
    suspend fun getExamMapForSubmissions(submissions: List<RealmSubmission>): Map<String?, RealmStepExam>
    suspend fun getStepExamByName(name: String?): RealmStepExam?
    suspend fun getExamQuestionCount(stepId: String): Int
    suspend fun createSurveySubmission(examId: String, userId: String?)
    suspend fun saveSubmission(submission: RealmSubmission)
    suspend fun updateSubmission(id: String, updater: (RealmSubmission) -> Unit)
}
