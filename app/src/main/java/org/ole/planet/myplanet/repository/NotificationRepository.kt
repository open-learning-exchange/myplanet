package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmSubmission

interface NotificationRepository {
    suspend fun updateResourceNotification(userId: String?)
    suspend fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    suspend fun getUnreadNotificationsSize(userId: String?): Int
}
