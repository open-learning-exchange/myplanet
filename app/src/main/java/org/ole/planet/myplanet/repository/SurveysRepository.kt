package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.ui.survey.SurveyFormState
import org.ole.planet.myplanet.ui.survey.SurveyInfo

interface SurveysRepository {
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

    suspend fun adoptSurvey(examId: String, teamId: String?, isTeam: Boolean): Boolean
}
