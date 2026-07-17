package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CreateExamSubmissionRequest
import org.ole.planet.myplanet.model.ExamAnswerData
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.SubmissionDetail
import org.ole.planet.myplanet.model.SubmissionItem

interface SubmissionsRepository {
    fun getPendingSurveysFlow(userId: String?): Flow<List<RealmSubmission>>
    fun getSubmissionsFlow(userId: String): Flow<List<RealmSubmission>>
    suspend fun getPendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getUniquePendingSurveys(userId: String?): List<RealmSubmission>
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getSubmissionsByIds(ids: List<String>): List<RealmSubmission>
    suspend fun getSubmissionsByUserId(userId: String): List<RealmSubmission>
    suspend fun getExamMap(submissions: List<RealmSubmission>): Map<String?, RealmStepExam>
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
    suspend fun saveSubmission(submission: RealmSubmission)
    suspend fun markSubmissionComplete(id: String, payload: JsonObject)
    suspend fun getSubmissionDetail(submissionId: String): SubmissionDetail?
    fun getNormalizedSubmitterName(submission: RealmSubmission): String?
    suspend fun getSubmissionsByParentId(parentId: String?, userId: String?, status: String? = null): List<RealmSubmission>
    suspend fun getSubmissionItems(parentId: String?, userId: String?): List<SubmissionItem>
    suspend fun deleteExamSubmissions(examId: String, courseId: String?, userId: String?)
    suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean
    suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean
    suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean
    suspend fun addSubmissionPhoto(submissionId: String?, examId: String?, courseId: String?, memberId: String?, photoPath: String?)
    suspend fun createExamSubmission(request: CreateExamSubmissionRequest): RealmSubmission?
    suspend fun saveExamAnswer(answerData: ExamAnswerData): Boolean
    suspend fun updateSubmissionStatus(submissionId: String?, status: String)
    suspend fun getExamByStepId(stepId: String): RealmStepExam?
    suspend fun getExamById(id: String): RealmStepExam?
    suspend fun getUnuploadedPhotos(): List<Pair<String?, JsonObject>>
    suspend fun markPhotoUploaded(photoId: String?, rev: String, id: String)
    suspend fun getOrCreateSubmission(userId: String?, parentId: String): RealmSubmission
    suspend fun getPhotosByIds(ids: Array<String>): List<RealmSubmitPhotos>
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    suspend fun insertSubmission(submission: JsonObject)
    suspend fun getExamUploadPayload(submission: RealmSubmission): JsonObject
    suspend fun serializeSubmission(submission: RealmSubmission, context: Context, source: String, parentCode: String): JsonObject
    suspend fun generateSubmissionPdf(submissionId: String): File?
    suspend fun generateMultipleSubmissionsPdf(submissionIds: List<String>, examTitle: String): File?
}
