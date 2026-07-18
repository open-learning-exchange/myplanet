package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.SurveyFormState
import org.ole.planet.myplanet.model.SurveyInfo

interface SurveysRepository {
    suspend fun getExamQuestions(examId: String): List<ExamQuestion>
    suspend fun getSurveySubmissionCount(userId: String?): Int
    suspend fun getTeamOwnedSurveys(teamId: String?): List<StepExam>
    suspend fun getAdoptableTeamSurveys(teamId: String?): List<StepExam>
    suspend fun getIndividualSurveys(): List<StepExam>
    suspend fun getSurveyInfos(
        isTeam: Boolean,
        teamId: String?,
        userId: String?,
        surveys: List<StepExam>
    ): Map<String, SurveyInfo>

    suspend fun getSurveyFormState(
        surveys: List<StepExam>,
        teamId: String?
    ): Map<String, SurveyFormState>

    suspend fun adoptSurvey(examId: String, userId: String?, teamId: String?, isTeam: Boolean)
    suspend fun getSurvey(id: String): StepExam?
    suspend fun getSurveys(): List<StepExam>
    suspend fun getSurveys(ascending: Boolean = false): List<StepExam>
    suspend fun bulkInsertExamsFromSync(jsonArray: JsonArray)
    fun dueRemindersFlow(): Flow<List<String>>
    suspend fun scheduleSurveyReminder(surveyIds: String, timeUnit: TimeUnit, value: Int)
    suspend fun setLastSurveyDialogShown(time: Long)
    suspend fun getLastSurveyDialogShown(): Long
    suspend fun isReminderScheduled(surveyIds: String): Boolean
    suspend fun getPendingAdoptedSurveys(): List<StepExam>
}
