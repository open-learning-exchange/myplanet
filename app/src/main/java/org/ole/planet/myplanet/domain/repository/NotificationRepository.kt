package org.ole.planet.myplanet.domain.repository

import org.ole.planet.myplanet.model.RealmSubmission

interface NotificationRepository {
    fun updateResourceNotification(userId: String?)
    fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?)
    fun getPendingSurveys(userId: String?): List<RealmSubmission>
    fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    fun getUnreadNotificationsSize(userId: String?): Int
}
