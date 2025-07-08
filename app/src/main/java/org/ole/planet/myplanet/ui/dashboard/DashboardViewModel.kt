package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.realm.Case
import io.realm.Realm
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.FileUtils.totalAvailableMemoryRatio
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

data class DashboardUiState(
    val unreadCount: Int = 0
)

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    fun calculateIndividualProgress(voiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(voiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(500)
    }

    fun calculateCommunityProgress(allVoiceCount: Int, hasUnfinishedSurvey: Boolean): Int {
        val earnedDollarsVoice = minOf(allVoiceCount, 5) * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        return total.coerceAtMost(11)
    }

    fun updateResourceNotification(realm: Realm, userId: String?) {
        val resourceCount = BaseResourceFragment.getLibraryList(realm, userId).size
        if (resourceCount > 0) {
            val existingNotification = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "resource")
                .findFirst()

            if (existingNotification != null) {
                existingNotification.message = "$resourceCount"
                existingNotification.relatedId = "$resourceCount"
            } else {
                createNotificationIfNotExists(realm, "resource", "$resourceCount", "$resourceCount", userId)
            }
        } else {
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "resource")
                .findFirst()?.deleteFromRealm()
        }
    }

    fun createNotificationIfNotExists(realm: Realm, type: String, message: String, relatedId: String?, userId: String?) {
        val existingNotification = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()

        if (existingNotification == null) {
            realm.createObject(RealmNotification::class.java, "${UUID.randomUUID()}").apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
    }

    fun getPendingSurveys(realm: Realm, userId: String?): List<RealmSubmission> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    fun getSurveyTitlesFromSubmissions(realm: Realm, submissions: List<RealmSubmission>): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }

    fun getUnreadNotificationsSize(realm: Realm, userId: String?): Int {
        return realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("isRead", false)
            .count()
            .toInt()
    }

    fun refreshNotifications(realm: Realm, userId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            var backgroundRealm: Realm? = null
            var unreadCount = 0
            try {
                backgroundRealm = Realm.getDefaultInstance()
                backgroundRealm.executeTransaction { r ->
                    updateResourceNotification(r, userId)
                    createSurveyNotifications(r, userId)
                    createTaskNotifications(r, userId)
                    createStorageNotification(r, userId)
                    createJoinRequestNotifications(r, userId)
                }
                unreadCount = getUnreadNotificationsSize(backgroundRealm, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                backgroundRealm?.close()
                _uiState.value = _uiState.value.copy(unreadCount = unreadCount)
            }
        }
    }

    private fun createSurveyNotifications(realm: Realm, userId: String?) {
        val pendingSurveys = getPendingSurveys(realm, userId)
        val surveyTitles = getSurveyTitlesFromSubmissions(realm, pendingSurveys)
        surveyTitles.forEach { title ->
            createNotificationIfNotExists(realm, "survey", title, title, userId)
        }
    }

    private fun createTaskNotifications(realm: Realm, userId: String?) {
        val tasks = realm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", userId)
            .findAll()
        tasks.forEach { task ->
            createNotificationIfNotExists(
                realm,
                "task",
                "${task.title} ${formatDate(task.deadline)}",
                task.id,
                userId
            )
        }
    }

    private fun createStorageNotification(realm: Realm, userId: String?) {
        val storageRatio = totalAvailableMemoryRatio
        createNotificationIfNotExists(realm, "storage", "$storageRatio", "storage", userId)
    }

    private fun createJoinRequestNotifications(realm: Realm, userId: String?) {
        val teamLeaderMemberships = realm.where(RealmMyTeam::class.java)
            .equalTo("userId", userId)
            .equalTo("docType", "membership")
            .equalTo("isLeader", true)
            .findAll()

        teamLeaderMemberships.forEach { leadership ->
            val pendingJoinRequests = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", leadership.teamId)
                .equalTo("docType", "request")
                .findAll()

            pendingJoinRequests.forEach { joinRequest ->
                val team = realm.where(RealmMyTeam::class.java)
                    .equalTo("_id", leadership.teamId)
                    .findFirst()

                val requester = realm.where(RealmUserModel::class.java)
                    .equalTo("id", joinRequest.userId)
                    .findFirst()

                val requesterName = requester?.name ?: "Unknown User"
                val teamName = team?.name ?: "Unknown Team"
                val message = "$requesterName has requested to join $teamName"

                createNotificationIfNotExists(
                    realm,
                    "join_request",
                    message,
                    joinRequest._id,
                    userId
                )
            }
        }
    }
}

