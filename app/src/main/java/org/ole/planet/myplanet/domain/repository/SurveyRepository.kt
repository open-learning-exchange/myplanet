package org.ole.planet.myplanet.domain.repository

import org.ole.planet.myplanet.model.RealmSubmission

interface SurveyRepository {
    fun getPendingSurveys(userId: String?): List<RealmSubmission>
    fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
}
