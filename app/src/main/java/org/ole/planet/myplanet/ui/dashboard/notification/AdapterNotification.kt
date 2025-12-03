package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.regex.Pattern
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    private val notificationRepository: NotificationRepository,
    notifications: List<RealmNotification>,
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (RealmNotification) -> Unit
) : ListAdapter<RealmNotification, AdapterNotification.ViewHolderNotifications>(
    DiffUtilExtensions.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    init {
        submitList(notifications)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        val rowNotificationsBinding = RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    fun updateNotifications(newNotifications: List<RealmNotification>) {
        submitList(newNotifications)
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: RealmNotification) {
            val context = rowNotificationsBinding.root.context
            itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                val currentNotification = formatNotificationMessage(notification, context)
                rowNotificationsBinding.title.text = Html.fromHtml(currentNotification, Html.FROM_HTML_MODE_LEGACY)
            }
            if (notification.isRead) {
                rowNotificationsBinding.btnMarkAsRead.visibility = View.GONE
                rowNotificationsBinding.root.alpha = 0.5f
            } else {
                rowNotificationsBinding.btnMarkAsRead.visibility = View.VISIBLE
                rowNotificationsBinding.root.alpha = 1.0f
                rowNotificationsBinding.btnMarkAsRead.setOnClickListener {
                    onMarkAsReadClick(notification.id)
                }
            }

            rowNotificationsBinding.root.setOnClickListener {
                onNotificationClick(notification)
            }
        }

        private suspend fun formatNotificationMessage(notification: RealmNotification, context: Context): String {
            return when (notification.type.lowercase()) {
                "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
                "task" -> {
                    val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
                    val matcher = datePattern.matcher(notification.message)
                    if (matcher.find()) {
                        val taskTitle = notification.message.substring(0, matcher.start()).trim()
                        val dateValue = notification.message.substring(matcher.start()).trim()
                        formatTaskNotification(context, taskTitle, dateValue)
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
                    val (requesterName, teamName) = notificationRepository.getJoinRequestDetails(notification.relatedId)
                    "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                            context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
                }
                else -> notification.message
            }
        }

        private suspend fun formatTaskNotification(context: Context, taskTitle: String, dateValue: String): String {
            val teamName = notificationRepository.getTaskTeamName(taskTitle)
            return if (teamName != null) {
                "<b>$teamName</b>: ${context.getString(R.string.task_notification, taskTitle, dateValue)}"
            } else {
                context.getString(R.string.task_notification, taskTitle, dateValue)
            }
        }
    }
}
