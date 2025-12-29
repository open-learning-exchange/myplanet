package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.ui.survey.SurveyBindingData
import org.ole.planet.myplanet.ui.survey.SurveyInfo

interface SurveyRepository {
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

    suspend fun getSurveyBindingData(
        surveys: List<RealmStepExam>,
        teamId: String?
    ): Map<String, SurveyBindingData>
}
