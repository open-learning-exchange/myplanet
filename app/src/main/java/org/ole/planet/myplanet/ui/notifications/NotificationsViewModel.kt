package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.repository.NotificationsRepository

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private var currentFilter: String = "all"

    fun loadNotifications(userId: String, filter: String) {
        currentFilter = filter
        viewModelScope.launch {
            val realmNotifications = notificationsRepository.getNotifications(userId, filter)
            _notifications.value = realmNotifications.map { formatNotification(it) }
        }
    }

    private suspend fun formatNotification(notification: RealmNotification): Notification {
        val formattedText = when (notification.type.lowercase()) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> {
                val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
                val matcher = datePattern.matcher(notification.message)
                if (matcher.find()) {
                    val taskTitle = notification.message.substring(0, matcher.start()).trim()
                    val dateValue = notification.message.substring(matcher.start()).trim()
                    formatTaskNotification(taskTitle, dateValue)
                } else {
                    notification.message
                }
            }
            "resource" -> {
                notification.message.toIntOrNull()?.let { count ->
                    context.getString(R.string.resource_notification, count)
                } ?: notification.message
            }
            "storage" -> {
                val storageValue = notification.message.replace("%", "").toIntOrNull()
                storageValue?.let {
                    when {
                        it <= 10 -> context.getString(R.string.storage_running_low) + " ${it}%"
                        it <= 40 -> context.getString(R.string.storage_running_low) + " ${it}%"
                        else -> context.getString(R.string.storage_available) + " ${it}%"
                    }
                } ?: notification.message
            }
            "join_request" -> {
                val (requesterName, teamName) = notificationsRepository.getJoinRequestDetails(notification.relatedId)
                "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                        context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
            }
            else -> notification.message
        }
        return Notification(
            id = notification.id,
            formattedText = formattedText,
            isRead = notification.isRead,
            type = notification.type,
            relatedId = notification.relatedId
        )
    }

    private suspend fun formatTaskNotification(taskTitle: String, dateValue: String): String {
        val teamName = notificationsRepository.getTaskTeamName(taskTitle)
        return if (teamName != null) {
            "<b>$teamName</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
        } else {
            context.getString(R.string.task_notification, taskTitle, dateValue)
        }
    }

    fun markAsRead(notificationId: String, userId: String) {
        viewModelScope.launch {
            notificationsRepository.markNotificationsAsRead(setOf(notificationId))
            loadNotifications(userId, currentFilter)
        }
    }

    fun markAllAsRead(userId: String) {
        viewModelScope.launch {
            notificationsRepository.markAllUnreadAsRead(userId)
            loadNotifications(userId, currentFilter)
        }
    }

    suspend fun getSurveyId(relatedId: String?): String? {
        return notificationsRepository.getSurveyId(relatedId)
    }

    suspend fun getTaskDetails(relatedId: String?): Triple<String, String?, String?>? {
        return notificationsRepository.getTaskDetails(relatedId)
    }

    suspend fun getJoinRequestTeamId(relatedId: String?): String? {
        return notificationsRepository.getJoinRequestTeamId(relatedId)
    }

    suspend fun getUnreadCount(userId: String): Int {
        return notificationsRepository.getUnreadCount(userId)
    }
}
