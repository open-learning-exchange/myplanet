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
import org.ole.planet.myplanet.repository.NotificationRepository

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<RealmNotification>>(emptyList())
    val notifications: StateFlow<List<RealmNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var userId: String = ""
    private var currentFilter: String = "all"

    fun initialize(userId: String) {
        this.userId = userId
        loadNotifications("all")
    }

    fun loadNotifications(filter: String) {
        currentFilter = filter
        viewModelScope.launch {
            _notifications.value = notificationRepository.getNotifications(userId, filter)
            _unreadCount.value = notificationRepository.getUnreadCount(userId)
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
            loadNotifications(currentFilter)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                notificationRepository.markAllAsRead(userId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                loadNotifications(currentFilter)
            }
        }
    }

    fun refresh() {
        loadNotifications(currentFilter)
    }

    fun clearError() {
        _error.value = null
    }
}
