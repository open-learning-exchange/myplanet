package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.ui.surveys.SurveyFormState

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
    suspend fun getSurveys(orderBy: String? = null, sort: io.realm.Sort = io.realm.Sort.ASCENDING): List<RealmStepExam>
}
