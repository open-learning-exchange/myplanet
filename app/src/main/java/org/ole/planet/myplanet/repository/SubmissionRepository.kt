package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

interface SubmissionRepository {
    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getSubmissionsByIds(ids: List<String>): List<RealmSubmission>
    suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission>
    suspend fun getExamMapForSubmissions(submissions: List<RealmSubmission>): Map<String?, RealmStepExam>
    suspend fun getExamQuestionCount(stepId: String): Int
    suspend fun hasSubmission(
        stepExamId: String?,
        courseId: String?,
        userId: String?,
        type: String,
    ): Boolean
    suspend fun hasPendingOfflineSubmissions(): Boolean
    suspend fun hasPendingExamResults(): Boolean
    suspend fun createSurveySubmission(examId: String, userId: String?)
    suspend fun saveSubmission(submission: RealmSubmission)
    suspend fun markSubmissionComplete(id: String, payload: com.google.gson.JsonObject)
}
