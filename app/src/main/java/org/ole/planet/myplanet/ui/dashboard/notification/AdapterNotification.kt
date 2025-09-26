package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    private val notificationRepository: NotificationRepository,
    private val coroutineScope: CoroutineScope,
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (RealmNotification) -> Unit
) : ListAdapter<RealmNotification, AdapterNotification.ViewHolderNotifications>(
    DiffUtilExtensions.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    init {
        submitList(emptyList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        val rowNotificationsBinding = RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    override fun onViewRecycled(holder: ViewHolderNotifications) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        private var metadataJob: Job? = null

        fun bind(notification: RealmNotification) {
            metadataJob?.cancel()
            val context = rowNotificationsBinding.root.context
            when (notification.type?.lowercase()) {
                "join_request" -> {
                    val baseMessage = formatJoinRequestMessage(context, requesterName = "Unknown User", teamName = "Unknown Team")
                    rowNotificationsBinding.title.text = Html.fromHtml(baseMessage, Html.FROM_HTML_MODE_LEGACY)
                    loadJoinRequestMetadata(notification)
                }
                "task" -> bindTaskNotification(notification, context)
                else -> {
                    val currentNotification = formatNotificationMessage(notification, context)
                    rowNotificationsBinding.title.text = Html.fromHtml(currentNotification, Html.FROM_HTML_MODE_LEGACY)
                }
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

        fun clear() {
            metadataJob?.cancel()
        }

        private fun formatNotificationMessage(notification: RealmNotification, context: Context): String {
            return when (notification.type?.lowercase()) {
                "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
                "task" -> formatTaskMessage(notification, context)
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
                else -> notification.message
            }
        }

        private fun bindTaskNotification(notification: RealmNotification, context: Context) {
            val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
            val matcher = datePattern.matcher(notification.message)
            if (matcher.find()) {
                val taskTitle = notification.message.substring(0, matcher.start()).trim()
                val dateValue = notification.message.substring(matcher.start()).trim()
                val baseMessage = context.getString(R.string.task_notification, taskTitle, dateValue)
                rowNotificationsBinding.title.text = Html.fromHtml(baseMessage, Html.FROM_HTML_MODE_LEGACY)
                metadataJob = coroutineScope.launch {
                    val metadata = withContext(Dispatchers.IO) { notificationRepository.getTaskNotificationMetadata(taskTitle) }
                    val teamName = metadata?.teamName
                    if (!teamName.isNullOrEmpty() && isBoundTo(notification.id)) {
                        val formatted = "<b>${teamName}</b>: $baseMessage"
                        rowNotificationsBinding.title.text = Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY)
                    }
                }
            } else {
                rowNotificationsBinding.title.text = Html.fromHtml(notification.message, Html.FROM_HTML_MODE_LEGACY)
            }
        }

        private fun formatJoinRequestMessage(context: Context, requesterName: String?, teamName: String?): String {
            return "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
        }

        private fun loadJoinRequestMetadata(notification: RealmNotification) {
            metadataJob = coroutineScope.launch {
                val metadata = withContext(Dispatchers.IO) {
                    notificationRepository.getJoinRequestMetadata(notification.relatedId)
                }
                val requesterName = metadata?.requesterName
                val teamName = metadata?.teamName
                if (isBoundTo(notification.id)) {
                    val context = rowNotificationsBinding.root.context
                    val formatted = formatJoinRequestMessage(context, requesterName, teamName)
                    rowNotificationsBinding.title.text = Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY)
                }
            }
        }

        private fun formatTaskMessage(notification: RealmNotification, context: Context): String {
            val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
            val matcher = datePattern.matcher(notification.message)
            return if (matcher.find()) {
                val taskTitle = notification.message.substring(0, matcher.start()).trim()
                val dateValue = notification.message.substring(matcher.start()).trim()
                context.getString(R.string.task_notification, taskTitle, dateValue)
            } else {
                notification.message
            }
        }

        private fun isBoundTo(notificationId: String): Boolean {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                return false
            }
            return this@AdapterNotification.currentList.getOrNull(position)?.id == notificationId
        }
    }
}
