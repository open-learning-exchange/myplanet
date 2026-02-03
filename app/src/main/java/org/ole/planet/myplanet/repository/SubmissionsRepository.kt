package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem

interface SubmissionsRepository {
    suspend fun getPendingSurveysFlow(userId: String?): Flow<List<RealmSubmission>>
    suspend fun getSubmissionsFlow(userId: String): Flow<List<RealmSubmission>>
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
    suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail?
    fun getNormalizedSubmitterName(submission: RealmSubmission): String?
    suspend fun getAllPendingSubmissions(): List<RealmSubmission>
    suspend fun getSubmissionsByParentId(parentId: String?, userId: String?, status: String? = null): List<RealmSubmission>
    suspend fun getSubmissionItems(parentId: String?, userId: String?): List<SubmissionItem>
    suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?)
    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean
    suspend fun getSurveysByCourseId(courseId: String): List<RealmStepExam>
    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean
    suspend fun generateSubmissionPdf(context: android.content.Context, submissionId: String): java.io.File?
    suspend fun generateMultipleSubmissionsPdf(context: android.content.Context, submissionIds: List<String>, examTitle: String): java.io.File?
    suspend fun addSubmissionPhoto(submissionId: String?, examId: String?, courseId: String?, memberId: String?, photoPath: String?)
    suspend fun createExamSubmission(userId: String?, userDob: String?, userGender: String?, exam: org.ole.planet.myplanet.model.RealmStepExam?, type: String?, teamId: String?): RealmSubmission?
    suspend fun saveExamAnswer(submission: RealmSubmission?, question: org.ole.planet.myplanet.model.RealmExamQuestion, ans: String, listAns: Map<String, String>?, otherText: String?, otherVisible: Boolean, type: String, index: Int, total: Int, isExplicitSubmission: Boolean): Boolean
    suspend fun getLastPendingSubmission(userId: String?): RealmSubmission?
    suspend fun updateSubmissionStatus(submissionId: String?, status: String)
}
