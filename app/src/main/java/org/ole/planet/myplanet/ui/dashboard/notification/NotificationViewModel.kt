package org.ole.planet.myplanet.ui.dashboard.notification

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
) : ViewModel() {
    private val _userId = MutableStateFlow<String?>(null)

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    private val _notifications = MutableStateFlow<List<RealmNotification>>(emptyList())
    val notifications: StateFlow<List<RealmNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun initialize(userId: String?) {
        val sanitizedUserId = userId?.takeIf { it.isNotBlank() }
        if (_userId.value == sanitizedUserId) {
            return
        }
        _userId.value = sanitizedUserId
        if (sanitizedUserId == null) {
            _notifications.value = emptyList()
            _unreadCount.value = 0
            return
        }
        refresh()
    }

    fun setFilter(filter: NotificationFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        refreshNotifications()
    }

    fun refresh() {
        if (_userId.value == null) {
            _notifications.value = emptyList()
            _unreadCount.value = 0
            return
        }
        refreshNotifications()
        refreshUnreadCount()
    }

    private fun refreshNotifications() {
        val targetUserId = _userId.value ?: return
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
        val targetUserId = _userId.value
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
        val targetUserId = _userId.value
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
