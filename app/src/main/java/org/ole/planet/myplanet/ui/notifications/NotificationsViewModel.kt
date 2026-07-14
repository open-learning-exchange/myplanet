package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.NotificationListItem
import org.ole.planet.myplanet.model.NotificationPayload
import org.ole.planet.myplanet.model.TaskNotificationResult
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedCount: StateFlow<Int> = _selectedIds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val groupedItems: StateFlow<List<NotificationListItem>> = combine(
        _notifications, _selectedIds, _collapsedGroups
    ) { notifs, selected, collapsed ->
        buildGroupedList(notifs, selected, collapsed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentFilter: String = "all"

    fun loadNotifications(userId: String, filter: String, isAdmin: Boolean = false) {
        currentFilter = filter
        viewModelScope.launch {
            val payloadNotifications = notificationsRepository.getNotifications(userId, filter, isAdmin)

            val taskIds = payloadNotifications
                .filter { it.type.lowercase() == "task" }
                .mapNotNull { it.relatedId }
                .distinct()
            val taskTeamNames = notificationsRepository.getTaskTeamNamesByTaskIds(taskIds).toMutableMap()

            val taskTitles = payloadNotifications
                .filter { it.type.lowercase() == "task" && (it.relatedId.isNullOrEmpty() || !taskTeamNames.containsKey(it.relatedId)) }
                .mapNotNull { parseTaskDate(it.message)?.first }
                .distinct()
            if (taskTitles.isNotEmpty()) {
                val teamNamesByTitle = notificationsRepository.getTaskTeamNamesByTaskTitles(taskTitles)
                taskTeamNames.putAll(teamNamesByTitle)
            }

            val joinRequestIds = payloadNotifications
                .filter { it.type.lowercase() == "join_request" }
                .mapNotNull { it.relatedId }
                .distinct()
            val joinRequestDetails = notificationsRepository.getJoinRequestDetailsBatch(joinRequestIds).toMutableMap()

            val joinRequestsWithoutRelatedId = payloadNotifications
                .filter { it.type.lowercase() == "join_request" && it.relatedId.isNullOrEmpty() }
            if (joinRequestsWithoutRelatedId.isNotEmpty()) {
                val fallbackDetail = notificationsRepository.getJoinRequestDetails(null)
                joinRequestDetails[""] = fallbackDetail
            }

            _notifications.value = payloadNotifications.map {
                formatNotification(it, taskTeamNames, joinRequestDetails)
            }
            _unreadCount.value = notificationsRepository.getUnreadCount(userId, isAdmin)
        }
    }

    fun toggleSelection(notificationId: String) {
        _selectedIds.update { current ->
            if (notificationId in current) current - notificationId else current + notificationId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleGroupExpansion(type: String) {
        _collapsedGroups.update { current ->
            if (type in current) current - type else current + type
        }
    }

    fun markSelectedAsRead() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val markedIds = notificationsRepository.markNotificationsAsRead(ids)
            if (markedIds.isNotEmpty()) {
                val wasUnreadCount = _notifications.value.count { it.id in markedIds && !it.isRead }
                _notifications.update { currentList ->
                    if (currentFilter == "unread") {
                        currentList.filterNot { it.id in markedIds }
                    } else {
                        currentList.map { notif ->
                            if (notif.id in markedIds && !notif.isRead) notif.copy(isRead = true) else notif
                        }
                    }
                }
                _unreadCount.update { maxOf(0, it - wasUnreadCount) }
                _selectedIds.value = emptySet()
            }
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val deletedIds = notificationsRepository.deleteNotifications(ids)
            if (deletedIds.isNotEmpty()) {
                val wasUnreadCount = _notifications.value.count { it.id in deletedIds && !it.isRead }
                _notifications.update { it.filterNot { n -> n.id in deletedIds } }
                _unreadCount.update { maxOf(0, it - wasUnreadCount) }
                _selectedIds.value = emptySet()
            }
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

    private fun buildGroupedList(
        notifications: List<Notification>,
        selectedIds: Set<String>,
        collapsedGroups: Set<String>
    ): List<NotificationListItem> {
        if (notifications.isEmpty()) return emptyList()
        val typeOrder = listOf("join_request", "team_join", "task", "survey", "chat", "voice_reply", "resource", "storage")
        // Normalize any unrecognized type to "notification" for a single Other group
        val grouped = notifications.groupBy { notif ->
            val t = notif.type.lowercase()
            if (t in KNOWN_TYPES) t else "notification"
        }
        val orderedTypes = (typeOrder.filter { grouped.containsKey(it) } +
                grouped.keys.filter { it !in typeOrder }).distinct()
        val inSelectionMode = selectedIds.isNotEmpty()
        return buildList {
            for (type in orderedTypes) {
                val items = grouped[type] ?: continue
                val unreadCount = items.count { !it.isRead }
                val isExpanded = type !in collapsedGroups
                add(NotificationListItem.Header(type, typeLabelFor(type), unreadCount, isExpanded))
                if (isExpanded) {
                    items.forEach { notification ->
                        add(NotificationListItem.Item(notification, notification.id in selectedIds, inSelectionMode))
                    }
                }
            }
        }
    }

    private fun resolveType(type: String, message: String): String {
        if (type.lowercase() in KNOWN_TYPES) return type.lowercase()
        val lower = message.lowercase()
        return when {
            lower.contains("requested to join") || lower.contains("wants to join") -> "join_request"
            lower.contains("added you to") || lower.contains("you've been added") || lower.contains("you have been added") -> "team_join"
            lower.contains("replied to your") || lower.contains("replied on your") || lower.contains("new reply to") -> "voice_reply"
            lower.contains("posted a new voice") || lower.contains("new voice in") || lower.contains("posted in") -> "chat"
            lower.contains("survey") -> "survey"
            lower.contains("is due") || lower.contains("due:") -> "task"
            lower.contains("storage") -> "storage"
            lower.contains("resource") -> "resource"
            else -> "notification"
        }
    }

    internal fun typeLabelFor(type: String): String = when (type.lowercase()) {
        "join_request" -> context.getString(R.string.notif_group_join_requests)
        "team_join" -> context.getString(R.string.notif_group_team_updates)
        "task" -> context.getString(R.string.tasks)
        "survey" -> context.getString(R.string.menu_surveys)
        "chat" -> context.getString(R.string.notif_group_new_voices)
        "voice_reply" -> context.getString(R.string.notif_group_voice_replies)
        "resource" -> context.getString(R.string.resources)
        "storage" -> context.getString(R.string.notification_group_system)
        else -> context.getString(R.string.notification_group_other)
    }

    companion object {
        val KNOWN_TYPES = setOf("join_request", "team_join", "task", "survey", "chat", "voice_reply", "resource", "storage")

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
            prefixStr: String,
            userRequestedToJoinTeamStr: String
        ): String {
            return "<b>$prefixStr</b> $userRequestedToJoinTeamStr"
        }
    }

    private fun formatNotification(
        notification: NotificationPayload,
        taskTeamNames: Map<String, String> = emptyMap(),
        joinRequestDetails: Map<String, Pair<String, String>> = emptyMap()
    ): Notification {
        val resolvedType = resolveType(notification.type, notification.message)
        val formattedText = when (resolvedType) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> {
                val parsedDate = parseTaskDate(notification.message)
                if (parsedDate != null) {
                    formatTaskNotification(parsedDate.first, parsedDate.second, notification.relatedId, taskTeamNames)
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
                if (notification.type.lowercase() != "join_request") {
                    // Server notification with pre-formatted message
                    notification.message
                } else {
                    val relatedId = notification.relatedId
                    val details = if (!relatedId.isNullOrEmpty()) {
                        joinRequestDetails[relatedId] ?: Pair("Unknown User", "Unknown Team")
                    } else {
                        joinRequestDetails[""] ?: Pair("Unknown User", "Unknown Team")
                    }
                    val requesterName = details.first
                    val teamName = details.second
                    val userRequestedStr = context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
                    formatJoinRequestNotification(
                        context.getString(R.string.join_request_prefix),
                        userRequestedStr
                    )
                }
            }
            else -> notification.message
        }
        return Notification(
            id = notification.id,
            formattedText = formattedText,
            isRead = notification.isRead,
            type = resolvedType,
            relatedId = notification.relatedId,
            createdAt = notification.createdAt,
            link = notification.link
        )
    }

    private fun formatTaskNotification(taskTitle: String, dateValue: String, relatedId: String?, taskTeamNames: Map<String, String> = emptyMap()): String {
        val teamName = if (!relatedId.isNullOrEmpty()) {
            taskTeamNames[relatedId] ?: taskTeamNames[taskTitle]
        } else {
            taskTeamNames[taskTitle]
        }
        return if (teamName != null) {
            "<b>$teamName</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
        } else {
            context.getString(R.string.task_notification, taskTitle, dateValue)
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
