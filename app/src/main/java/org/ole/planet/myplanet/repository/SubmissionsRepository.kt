package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem

interface SubmissionsRepository {
    fun getPendingSurveysFlow(userId: String?): Flow<List<Submission>>
    fun getSubmissionsFlow(userId: String): Flow<List<Submission>>
    suspend fun getPendingSurveys(userId: String?): List<Submission>
    suspend fun getUniquePendingSurveys(userId: String?): List<Submission>
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<Submission>): List<String>
    suspend fun getSubmissionById(id: String): Submission?
    suspend fun getSubmissionsByIds(ids: List<String>): List<Submission>
    suspend fun getSubmissionsByUserId(userId: String): List<Submission>
    suspend fun getExamMap(submissions: List<Submission>): Map<String?, StepExam>
    suspend fun getExamQuestionCount(stepId: String): Int
    suspend fun hasSubmission(
        stepExamId: String?,
        courseId: String?,
        userId: String?,
        type: String,
    ): Boolean
    suspend fun hasPendingOfflineSubmissions(): Boolean
    suspend fun hasPendingExamResults(): Boolean
    suspend fun createBulkSurveySubmissions(examId: String, userIds: List<String>)
    suspend fun saveSubmission(submission: Submission)
    suspend fun markSubmissionComplete(id: String, payload: JsonObject)
    suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail?
    fun getNormalizedSubmitterName(submission: Submission): String?
    suspend fun getSubmissionsByParentId(parentId: String?, userId: String?, status: String? = null): List<Submission>
    suspend fun getSubmissionItems(parentId: String?, userId: String?): List<SubmissionItem>
    suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?)
    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean
    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean
    suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean
    suspend fun addSubmissionPhoto(submissionId: String?, examId: String?, courseId: String?, memberId: String?, photoPath: String?)
    suspend fun createExamSubmission(request: CreateExamSubmissionRequest): Submission?
    suspend fun saveExamAnswer(answerData: ExamAnswerData): Boolean
    suspend fun getLastPendingSubmission(userId: String?): Submission?
    suspend fun updateSubmissionStatus(submissionId: String?, status: String)
    suspend fun getExamByStepId(stepId: String): StepExam?
    suspend fun getExamById(id: String): StepExam?
    suspend fun getUnuploadedPhotos(): List<Pair<String?, JsonObject>>
    suspend fun markPhotoUploaded(photoId: String?, rev: String, id: String)
    suspend fun getOrCreateSubmission(userId: String?, parentId: String): Submission
    suspend fun getPhotosByIds(ids: Array<String>): List<SubmitPhotos>
    suspend fun bulkInsertFromSync(jsonArray: JsonArray)
    suspend fun insertSubmission(submission: JsonObject)
    suspend fun getExamUploadPayload(submission: Submission): JsonObject
    suspend fun serializeSubmission(submission: Submission, context: Context, source: String, parentCode: String): JsonObject
    suspend fun generateSubmissionPdf(submissionId: String): File?
    suspend fun generateMultipleSubmissionsPdf(submissionIds: List<String>, examTitle: String): File?
    suspend fun getPendingExamResults(): List<Submission>
    suspend fun getPendingSubmissionsForUpload(): List<Submission>
}
