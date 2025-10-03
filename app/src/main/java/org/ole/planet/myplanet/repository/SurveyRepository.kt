package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam

interface SurveyRepository {
    suspend fun getTeamOwnedSurveys(teamId: String?): List<RealmStepExam>
    suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam>
    suspend fun getIndividualSurveys(): List<RealmStepExam>
}
