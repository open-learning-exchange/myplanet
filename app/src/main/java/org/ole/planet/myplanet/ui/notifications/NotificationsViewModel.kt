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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.repository.NotificationsRepository

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private var currentFilter: String = "all"

    fun loadNotifications(userId: String, filter: String) {
        currentFilter = filter
        viewModelScope.launch {
            val realmNotifications = notificationsRepository.getNotifications(userId, filter)
            _notifications.value = realmNotifications.map { formatNotification(it) }
            _unreadCount.value = notificationsRepository.getUnreadCount(userId)
        }
    }

    companion object {
        private val TASK_DATE_PATTERN = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")

        internal fun parseTaskDate(message: String): Pair<String, String>? {
            val matcher = TASK_DATE_PATTERN.matcher(message)
            return if (matcher.find()) {
                val taskTitle = message.substring(0, matcher.start()).trim()
                val dateValue = message.substring(matcher.start()).trim()
                Pair(taskTitle, dateValue)
            } else {
                null
            }
        }

        internal fun formatStorageNotification(message: String, storageRunningLowStr: String, storageAvailableStr: String): String {
            val storageValue = message.replace("%", "").toIntOrNull()
            return storageValue?.let {
                when {
                    it <= 10 -> "$storageRunningLowStr ${it}%"
                    it <= 40 -> "$storageRunningLowStr ${it}%"
                    else -> "$storageAvailableStr ${it}%"
                }
            } ?: message
        }

        internal fun formatJoinRequestNotification(
            requesterName: String,
            teamName: String,
            prefixStr: String,
            userRequestedToJoinTeamStr: String
        ): String {
            return "<b>$prefixStr</b> $userRequestedToJoinTeamStr"
        }
    }

    private suspend fun formatNotification(notification: RealmNotification): Notification {
        val formattedText = when (notification.type.lowercase()) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> {
                val parsedDate = parseTaskDate(notification.message)
                if (parsedDate != null) {
                    formatTaskNotification(parsedDate.first, parsedDate.second)
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
                formatStorageNotification(
                    notification.message,
                    context.getString(R.string.storage_running_low),
                    context.getString(R.string.storage_available)
                )
            }
            "join_request" -> {
                val (requesterName, teamName) = notificationsRepository.getJoinRequestDetails(notification.relatedId)
                val userRequestedStr = context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
                formatJoinRequestNotification(
                    requesterName,
                    teamName,
                    context.getString(R.string.join_request_prefix),
                    userRequestedStr
                )
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

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            val markedIds = notificationsRepository.markNotificationsAsRead(setOf(notificationId))
            if (markedIds.contains(notificationId)) {
                var wasUnread = false
                _notifications.update { currentList ->
                    val targetNotification = currentList.find { it.id == notificationId }
                    if (targetNotification != null && !targetNotification.isRead) {
                        wasUnread = true
                        if (currentFilter == "unread") {
                            currentList.filter { it.id != notificationId }
                        } else {
                            currentList.map {
                                if (it.id == notificationId) it.copy(isRead = true) else it
                            }
                        }
                    } else {
                        currentList
                    }
                }
                if (wasUnread && _unreadCount.value > 0) {
                    _unreadCount.value -= 1
                }
            }
        }
    }

    fun markAllAsRead(userId: String) {
        viewModelScope.launch {
            val markedIds = notificationsRepository.markAllUnreadAsRead(userId)
            if (markedIds.isNotEmpty()) {
                _notifications.update { currentList ->
                    if (currentFilter == "unread") {
                        currentList.filterNot { it.id in markedIds }
                    } else {
                        currentList.map {
                            if (it.id in markedIds && !it.isRead) it.copy(isRead = true) else it
                        }
                    }
                }
                _unreadCount.value = 0
            }
        }
    }

    suspend fun getSurveyId(relatedId: String?): String? {
        return notificationsRepository.getSurveyId(relatedId)
    }

    suspend fun getTaskDetails(relatedId: String?): TaskNotificationResult? {
        return notificationsRepository.getTaskDetails(relatedId)
    }

    suspend fun getJoinRequestTeamId(relatedId: String?): String? {
        return notificationsRepository.getJoinRequestTeamId(relatedId)
    }

    suspend fun getUnreadCount(userId: String): Int {
        return notificationsRepository.getUnreadCount(userId)
    }
}
