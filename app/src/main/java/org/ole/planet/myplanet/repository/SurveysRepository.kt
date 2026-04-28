package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmExamQuestion
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.SurveyFormState
import org.ole.planet.myplanet.model.SurveyInfo

interface SurveysRepository {
    suspend fun getExamQuestions(examId: String): List<RealmExamQuestion>
    suspend fun getSurveySubmissionCount(userId: String?): Int
    suspend fun getTeamOwnedSurveys(teamId: String?): List<RealmStepExam>
    suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam>
    suspend fun getIndividualSurveys(): List<RealmStepExam>
    suspend fun getSurveyInfos(
        isTeam: Boolean,
        teamId: String?,
        userId: String?,
        surveys: List<RealmStepExam>
    ): Map<String, SurveyInfo>

    suspend fun getSurveyFormState(
        surveys: List<RealmStepExam>,
        teamId: String?
    ): Map<String, SurveyFormState>

    suspend fun adoptSurvey(examId: String, userId: String?, teamId: String?, isTeam: Boolean)
    suspend fun getSurvey(id: String): RealmStepExam?
    suspend fun getSurveys(): List<RealmStepExam>
    suspend fun getSurveys(orderBy: String, sort: io.realm.Sort): List<RealmStepExam>
    fun bulkInsertExamsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)
    fun dueRemindersFlow(): Flow<List<String>>
    suspend fun scheduleSurveyReminder(surveyIds: String, timeUnit: TimeUnit, value: Int)
    suspend fun setLastSurveyDialogShown(time: Long)
    suspend fun getLastSurveyDialogShown(): Long
    suspend fun isReminderScheduled(surveyIds: String): Boolean
}