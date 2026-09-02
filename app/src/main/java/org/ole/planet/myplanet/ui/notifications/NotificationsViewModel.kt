package org.ole.planet.myplanet.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())
    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedCount: StateFlow<Int> = _selectedIds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private data class NotificationGroup(
        val type: String,
        val label: String,
        val unreadCount: Int,
        val items: List<Notification>
    )

    private val groupedNotifications: StateFlow<List<NotificationGroup>> = _notifications
        .map { buildNotificationGroups(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedItems: StateFlow<List<NotificationListItem>> = combine(
        groupedNotifications, _selectedIds, _collapsedGroups, _expandedGroups
    ) { groups, selected, collapsed, expanded ->
        buildGroupedList(groups, selected, collapsed, expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentFilter: String = "all"

    fun loadNotifications(userId: String, filter: String, isAdmin: Boolean = false) {
        currentFilter = filter
        viewModelScope.launch {
            val payloadNotifications = notificationsRepository.getNotifications(userId, filter, isAdmin)

            val taskNotifications = mutableListOf<NotificationPayload>()
            val joinRequestNotifications = mutableListOf<NotificationPayload>()
            for (notification in payloadNotifications) {
                if (notification.type.equals("task", ignoreCase = true)) {
                    taskNotifications.add(notification)
                } else if (notification.type.equals("join_request", ignoreCase = true)) {
                    joinRequestNotifications.add(notification)
                }
            }

            val taskIds = taskNotifications
                .mapNotNull { it.relatedId }
                .distinct()

            val taskTitles = taskNotifications
                .mapNotNull { parseTaskDate(it.message)?.first }
                .distinct()

            val joinRequestIds = joinRequestNotifications
                .mapNotNull { it.relatedId }
                .distinct()

            val joinRequestsWithoutRelatedId = joinRequestNotifications
                .filter { it.relatedId.isNullOrEmpty() }

            val (taskTeamNames, joinRequestDetails, unreadCount) = coroutineScope {
                val taskTeamNamesByIdsDeferred = async {
                    notificationsRepository.getTaskTeamNamesByTaskIds(taskIds)
                }

                val taskTeamNamesByTitlesDeferred = async {
                    if (taskTitles.isNotEmpty()) {
                        notificationsRepository.getTaskTeamNamesByTaskTitles(taskTitles)
                    } else {
                        emptyMap()
                    }
                }

                val joinRequestDetailsDeferred = async {
                    val details = notificationsRepository.getJoinRequestDetailsBatch(joinRequestIds).toMutableMap()
                    if (joinRequestsWithoutRelatedId.isNotEmpty()) {
                        val fallbackDetail = notificationsRepository.getJoinRequestDetails(null)
                        details[""] = fallbackDetail
                    }
                    details
                }

                val unreadCountDeferred = async {
                    notificationsRepository.getUnreadCount(userId, isAdmin)
                }

                val combinedTaskTeamNames = taskTeamNamesByTitlesDeferred.await().toMutableMap().apply {
                    putAll(taskTeamNamesByIdsDeferred.await())
                }

                Triple(
                    combinedTaskTeamNames,
                    joinRequestDetailsDeferred.await(),
                    unreadCountDeferred.await()
                )
            }

            _notifications.value = payloadNotifications.map {
                formatNotification(it, taskTeamNames, joinRequestDetails)
            }
            _unreadCount.value = unreadCount
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
        val isCurrentlyExpanded = when {
            type in _expandedGroups.value -> true
            type in _collapsedGroups.value -> false
            else -> isGroupDefaultExpanded(type, notifications.value)  // If at least one notification is not read in the group, expand
        }

        if (isCurrentlyExpanded) {
            // It's expanded, collapse it
            _expandedGroups.update { it - type }
            _collapsedGroups.update { it + type }
        } else {
            // It's collapsed, expand it
            _collapsedGroups.update { it - type }
            _expandedGroups.update { it + type }
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
                        currentList.markAsRead(markedIds)
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
                    currentList.mapNotNull { notif ->
                        if (notif.id == notificationId) {
                            if (!notif.isRead) {
                                wasUnread = true
                                if (currentFilter == "unread") null
                                else notif.copy(isRead = true)
                            } else {
                                notif
                            }
                        } else {
                            notif
                        }
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
                        currentList.markAsRead(markedIds)
                    }
                }
                _unreadCount.value = 0
                _expandedGroups.value = emptySet()
                _collapsedGroups.value = emptySet()
            }
        }
    }

    private fun List<Notification>.markAsRead(id: String): List<Notification> {
        return map { if (it.id == id && !it.isRead) it.copy(isRead = true) else it }
    }
    private fun List<Notification>.markAsRead(ids: Set<String>): List<Notification> {
        return map { if (it.id in ids && !it.isRead) it.copy(isRead = true) else it }
    }

    private fun isGroupDefaultExpanded(type: String, notifications: List<Notification>): Boolean {
        return notifications.any { it.type == type && !it.isRead }
    }

    private fun buildNotificationGroups(notifications: List<Notification>): List<NotificationGroup> {
        if (notifications.isEmpty()) return emptyList()
        val grouped = notifications.groupBy { notif ->
            val t = notif.type.lowercase(Locale.ROOT)
            if (t in NotificationsRepository.KNOWN_TYPES) t else "notification"
        }
        val orderedTypes = (TYPE_ORDER.filter { grouped.containsKey(it) } +
                grouped.keys.filter { it !in TYPE_ORDER }).distinct()
        return orderedTypes.mapNotNull { type ->
            val items = grouped[type] ?: return@mapNotNull null
            val unreadCount = items.count { !it.isRead }
            NotificationGroup(
                type = type,
                label = typeLabelFor(type),
                unreadCount = unreadCount,
                items = items
            )
        }
    }

    private fun buildGroupedList(
        groups: List<NotificationGroup>,
        selectedIds: Set<String>,
        collapsedGroups: Set<String>,
        expandedGroups: Set<String>
    ): List<NotificationListItem> {
        if (groups.isEmpty()) return emptyList()
        val inSelectionMode = selectedIds.isNotEmpty()
        return buildList {
            for (group in groups) {
                val isExpanded = when {
                    group.type in expandedGroups -> true
                    group.type in collapsedGroups -> false
                    else -> group.unreadCount > 0
                }
                add(NotificationListItem.Header(group.type, group.label, group.unreadCount, isExpanded))
                if (isExpanded) {
                    group.items.forEach { notification ->
                        add(NotificationListItem.Item(notification, notification.id in selectedIds, inSelectionMode))
                    }
                }
            }
        }
    }

    private fun typeLabelFor(type: String): String = when (type.lowercase(Locale.ROOT)) {
        "join_request" -> context.getString(R.string.notif_group_join_requests)
        "team_join" -> context.getString(R.string.notif_group_team_updates)
        "task" -> context.getString(R.string.tasks)
        "chat" -> context.getString(R.string.notif_group_new_voices)
        "voice_reply" -> context.getString(R.string.notif_group_voice_replies)
        "resource" -> context.getString(R.string.resources)
        "storage" -> context.getString(R.string.notification_group_system)
        else -> context.getString(R.string.notification_group_other)
    }

    companion object {
        val TYPE_ORDER = listOf("join_request", "team_join", "task", "chat", "voice_reply", "resource", "storage")

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
        val resolvedType = notificationsRepository.resolveType(notification.type, notification.message, notification.subType)
        val formattedText = when (resolvedType) {
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
                if (!notification.type.equals("join_request", ignoreCase = true)) {
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
