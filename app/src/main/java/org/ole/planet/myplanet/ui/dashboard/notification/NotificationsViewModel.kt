package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import java.util.regex.Pattern

class NotificationsViewModel(
    private val databaseService: DatabaseService,
    appContext: Context,
) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _notificationItems = MutableStateFlow<List<NotificationDisplayItem>>(emptyList())
    val notificationItems: StateFlow<List<NotificationDisplayItem>> = _notificationItems.asStateFlow()

    private val notificationCache = mutableMapOf<String, CachedNotification>()
    private var currentUserId: String = ""
    private var currentFilter: NotificationFilter = NotificationFilter.ALL

    fun loadNotifications(userId: String = currentUserId, filter: NotificationFilter = currentFilter) {
        currentUserId = userId
        currentFilter = filter
        if (userId.isEmpty()) {
            _notificationItems.value = emptyList()
            return
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    val query = realm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                    when (filter) {
                        NotificationFilter.READ -> query.equalTo("isRead", true)
                        NotificationFilter.UNREAD -> query.equalTo("isRead", false)
                        NotificationFilter.ALL -> {}
                    }
                    val realmResults = query
                        .sort("isRead", Sort.ASCENDING, "createdAt", Sort.DESCENDING)
                        .findAll()
                    val notifications = realm.copyFromRealm(realmResults)
                        .filter { it.message.isNotEmpty() && it.message != "INVALID" }
                    notifications.map { notification ->
                        val signature = NotificationSignature.from(notification)
                        val cached = notificationCache[notification.id]
                        if (cached != null && cached.signature == signature) {
                            ComputationResult(
                                cached.item.copy(
                                    message = notification.message,
                                    isRead = notification.isRead,
                                    createdAt = notification.createdAt,
                                    relatedId = notification.relatedId,
                                    title = notification.title,
                                ),
                                signature,
                            )
                        } else {
                            val displayHtml = formatNotificationMessage(realm, notification)
                            val item = notification.toDisplayItem(displayHtml)
                            ComputationResult(item, signature)
                        }
                    }
                }
            }
            val updatedItems = results.map { it.item }
            val updatedCache = results.associate { result ->
                result.item.id to CachedNotification(result.signature, result.item)
            }
            notificationCache.clear()
            notificationCache.putAll(updatedCache)
            _notificationItems.value = updatedItems
        }
    }

    private fun RealmNotification.toDisplayItem(displayHtml: String): NotificationDisplayItem {
        return NotificationDisplayItem(
            id = id,
            userId = userId,
            message = message,
            isRead = isRead,
            createdAt = createdAt,
            type = type,
            relatedId = relatedId,
            title = title,
            displayHtml = displayHtml,
        )
    }

    private fun formatNotificationMessage(
        realm: Realm,
        notification: RealmNotification,
    ): String {
        val context = appContext
        return when (notification.type.lowercase()) {
            "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
            "task" -> formatTaskNotification(realm, notification)
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
            "join_request" -> formatJoinRequestNotification(realm, notification)
            else -> notification.message
        }
    }

    private fun formatJoinRequestNotification(
        realm: Realm,
        notification: RealmNotification,
    ): String {
        val context = appContext
        val joinRequest = realm.where(RealmMyTeam::class.java)
            .equalTo("_id", notification.relatedId)
            .equalTo("docType", "request")
            .findFirst()
        val team = joinRequest?.teamId?.let { tid ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("_id", tid)
                .findFirst()
        }
        val requester = joinRequest?.userId?.let { uid ->
            realm.where(RealmUserModel::class.java)
                .equalTo("id", uid)
                .findFirst()
        }
        val requesterName = requester?.name ?: "Unknown User"
        val teamName = team?.name ?: "Unknown Team"
        return "<b>${context.getString(R.string.join_request_prefix)}</b> " +
            context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
    }

    private fun formatTaskNotification(
        realm: Realm,
        notification: RealmNotification,
    ): String {
        val context = appContext
        val matcher = DATE_PATTERN.matcher(notification.message)
        return if (matcher.find()) {
            val taskTitle = notification.message.substring(0, matcher.start()).trim()
            val dateValue = notification.message.substring(matcher.start()).trim()
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
        } else {
            notification.message
        }
    }

    private data class ComputationResult(
        val item: NotificationDisplayItem,
        val signature: NotificationSignature,
    )

    private data class CachedNotification(
        val signature: NotificationSignature,
        val item: NotificationDisplayItem,
    )

    private data class NotificationSignature(
        val message: String,
        val relatedId: String?,
        val type: String,
        val title: String?,
    ) {
        companion object {
            fun from(notification: RealmNotification): NotificationSignature {
                return NotificationSignature(
                    message = notification.message,
                    relatedId = notification.relatedId,
                    type = notification.type,
                    title = notification.title,
                )
            }
        }
    }

    companion object {
        private val DATE_PATTERN: Pattern =
            Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")

        fun provideFactory(
            databaseService: DatabaseService,
            appContext: Context,
        ): ViewModelProvider.Factory {
            val applicationContext = appContext.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return NotificationsViewModel(databaseService, applicationContext) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${'$'}modelClass")
                }
            }
        }
    }
}

enum class NotificationFilter(val value: String) {
    ALL("all"),
    READ("read"),
    UNREAD("unread");

    companion object {
        fun fromValue(value: String): NotificationFilter {
            return entries.firstOrNull { it.value == value } ?: ALL
        }
    }
}
