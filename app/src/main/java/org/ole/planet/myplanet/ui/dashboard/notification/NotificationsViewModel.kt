package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NotificationFilter
import org.ole.planet.myplanet.repository.NotificationRepository

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val databaseService: DatabaseService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<RealmNotification>>(emptyList())
    val notifications: StateFlow<List<RealmNotification>> = _notifications.asStateFlow()

    private val _notificationItems = MutableStateFlow<List<NotificationUiModel>>(emptyList())
    val notificationItems: StateFlow<List<NotificationUiModel>> = _notificationItems.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _events = MutableSharedFlow<NotificationEvent>()
    val events: SharedFlow<NotificationEvent> = _events.asSharedFlow()

    private var currentUserId: String? = null
    private var currentFilter: NotificationFilter = NotificationFilter.ALL

    fun initialize(userId: String) {
        if (currentUserId == userId) {
            refresh()
            return
        }
        currentUserId = userId
        loadNotifications()
    }

    fun updateFilter(filter: NotificationFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        loadNotifications()
    }

    fun refresh() {
        if (currentUserId.isNullOrBlank()) return
        loadNotifications()
    }

    fun markNotificationAsRead(notificationId: String) {
        markNotificationsAsRead(setOf(notificationId), isMarkAll = false)
    }

    fun markAllAsRead() {
        markNotificationsAsRead(emptySet(), isMarkAll = true)
    }

    private fun loadNotifications() {
        val userId = currentUserId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            try {
                val notifications = notificationRepository.getNotifications(userId, currentFilter)
                val items = buildNotificationUiItems(notifications)
                val unreadCount = notificationRepository.getUnreadCount(userId)
                _notifications.value = notifications
                _notificationItems.value = items
                _unreadCount.value = unreadCount
            } catch (throwable: Throwable) {
                _events.emit(NotificationEvent.ShowMessage(R.string.failed_to_load_notifications))
            }
        }
    }

    private fun markNotificationsAsRead(notificationIds: Set<String>, isMarkAll: Boolean) {
        val userId = currentUserId?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val previousNotifications = _notifications.value
            val previousUnreadCount = _unreadCount.value
            val idsToUpdate = if (isMarkAll) {
                previousNotifications.filter { !it.isRead }.map { it.id }.toSet()
            } else {
                notificationIds
            }

            if (idsToUpdate.isEmpty()) {
                return@launch
            }

            val updatedList = getUpdatedListAfterMarkingRead(previousNotifications, idsToUpdate, currentFilter)
            val updatedItems = buildNotificationUiItems(updatedList)
            _notifications.value = updatedList
            _notificationItems.value = updatedItems

            val unreadMarkedCount = if (isMarkAll) {
                previousUnreadCount
            } else {
                previousNotifications.count { idsToUpdate.contains(it.id) && !it.isRead }
            }
            _unreadCount.value = if (isMarkAll) 0 else (previousUnreadCount - unreadMarkedCount).coerceAtLeast(0)

            try {
                val clearedIds = if (isMarkAll) {
                    if (userId != null) notificationRepository.markAllUnreadAsRead(userId) else emptySet()
                } else {
                    notificationRepository.markNotificationsAsRead(idsToUpdate)
                }
                if (clearedIds.isNotEmpty()) {
                    _events.emit(NotificationEvent.ClearNotifications(clearedIds))
                }
            } catch (throwable: Throwable) {
                _notifications.value = previousNotifications
                _notificationItems.value = buildNotificationUiItems(previousNotifications)
                _unreadCount.value = previousUnreadCount
                _events.emit(NotificationEvent.ShowMessage(R.string.failed_to_mark_as_read))
            }
        }
    }

    private suspend fun buildNotificationUiItems(
        notifications: List<RealmNotification>,
    ): List<NotificationUiModel> {
        return withContext(Dispatchers.Default) {
            notifications.map { notification ->
                NotificationUiModel(
                    notification = notification,
                    displayText = formatNotificationMessage(notification)
                )
            }
        }
    }

    private fun formatNotificationMessage(notification: RealmNotification): CharSequence {
        val formattedString = when (notification.type.lowercase()) {
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
            "join_request" -> formatJoinRequestNotification(notification)
            else -> notification.message
        }

        return HtmlCompat.fromHtml(formattedString, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun formatTaskNotification(taskTitle: String, dateValue: String): String {
        return databaseService.withRealm { realm ->
            val taskObj = realm.where(RealmTeamTask::class.java)
                .equalTo("title", taskTitle)
                .findFirst()
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", taskObj?.teamId)
                .findFirst()
            if (team?.name != null) {
                "<b>${team.name}</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
            } else {
                context.getString(R.string.task_notification, taskTitle, dateValue)
            }
        } ?: context.getString(R.string.task_notification, taskTitle, dateValue)
    }

    private fun formatJoinRequestNotification(notification: RealmNotification): String {
        val joinRequestId = notification.relatedId ?: return notification.message
        val actualJoinRequestId = if (joinRequestId.startsWith("join_request_")) {
            joinRequestId.removePrefix("join_request_")
        } else {
            joinRequestId
        }
        return databaseService.withRealm { realm ->
            val joinRequest = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", actualJoinRequestId)
                .equalTo("docType", "request")
                .findFirst()
            val team = joinRequest?.teamId?.let { teamId ->
                realm.where(RealmMyTeam::class.java)
                    .equalTo("_id", teamId)
                    .findFirst()
            }
            val requester = joinRequest?.userId?.let { userId ->
                realm.where(RealmUserModel::class.java)
                    .equalTo("id", userId)
                    .findFirst()
            }
            val requesterName = requester?.name ?: "Unknown User"
            val teamName = team?.name ?: "Unknown Team"
            "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
        } ?: notification.message
    }

    private fun getUpdatedListAfterMarkingRead(
        currentList: List<RealmNotification>,
        notificationIds: Set<String>,
        selectedFilter: NotificationFilter,
    ): List<RealmNotification> {
        return if (selectedFilter == NotificationFilter.UNREAD) {
            currentList.filterNot { notificationIds.contains(it.id) }
        } else {
            sortNotifications(
                currentList.map { notification ->
                    if (notificationIds.contains(notification.id) && !notification.isRead) {
                        notification.asReadCopy()
                    } else {
                        notification
                    }
                }
            )
        }
    }

    private fun sortNotifications(notifications: List<RealmNotification>): List<RealmNotification> {
        return notifications.sortedWith(compareBy<RealmNotification> { it.isRead }.thenByDescending { it.createdAt })
    }

    private fun RealmNotification.asReadCopy(): RealmNotification {
        return RealmNotification().also { copy ->
            copy.id = id
            copy.userId = userId
            copy.message = message
            copy.isRead = true
            copy.createdAt = Date()
            copy.type = type
            copy.relatedId = relatedId
            copy.title = title
        }
    }
}

data class NotificationUiModel(
    val notification: RealmNotification,
    val displayText: CharSequence,
)

sealed class NotificationEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : NotificationEvent()
    data class ClearNotifications(val notificationIds: Set<String>) : NotificationEvent()
}
