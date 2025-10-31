package org.ole.planet.myplanet.ui.dashboard.notification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.repository.NotificationFilter
import org.ole.planet.myplanet.repository.NotificationRepository

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String? = savedStateHandle.get<String>("userId")?.takeIf { it.isNotBlank() }

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    private val _notifications = MutableStateFlow<List<RealmNotification>>(emptyList())
    val notifications: StateFlow<List<RealmNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        refresh()
    }

    fun setFilter(filter: NotificationFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        refreshNotifications()
    }

    fun refresh() {
        refreshNotifications()
        refreshUnreadCount()
    }

    private fun refreshNotifications() {
        val targetUserId = userId
        if (targetUserId == null) {
            _notifications.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching {
                notificationRepository.getNotifications(targetUserId, _filter.value)
            }.onSuccess { notifications ->
                _notifications.value = notifications
            }.onFailure {
                _notifications.value = emptyList()
            }
        }
    }

    private fun refreshUnreadCount() {
        val targetUserId = userId
        if (targetUserId == null) {
            _unreadCount.value = 0
            return
        }
        viewModelScope.launch {
            runCatching {
                notificationRepository.getUnreadCount(targetUserId)
            }.onSuccess { count ->
                _unreadCount.value = count
            }.onFailure {
                _unreadCount.value = 0
            }
        }
    }

    fun markNotificationsAsRead(
        notificationIds: Set<String>,
        onSuccess: (Set<String>) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        if (notificationIds.isEmpty()) {
            onSuccess(emptySet())
            return
        }
        viewModelScope.launch {
            runCatching {
                notificationRepository.markNotificationsAsRead(notificationIds)
            }.onSuccess { updatedIds ->
                onSuccess(updatedIds)
                refresh()
            }.onFailure { throwable ->
                onError(throwable)
            }
        }
    }

    fun markAllAsRead(
        onSuccess: (Set<String>) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val targetUserId = userId
        if (targetUserId == null) {
            onSuccess(emptySet())
            return
        }
        viewModelScope.launch {
            runCatching {
                notificationRepository.markAllUnreadAsRead(targetUserId)
            }.onSuccess { updatedIds ->
                onSuccess(updatedIds)
                refresh()
            }.onFailure { throwable ->
                onError(throwable)
            }
        }
    }
}
