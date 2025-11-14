package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    private val databaseService: DatabaseService,
    notifications: List<Notification>,
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (Notification) -> Unit
) : ListAdapter<Notification, AdapterNotification.ViewHolderNotifications>(
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

    fun updateNotifications(newNotifications: List<Notification>) {
        submitList(newNotifications)
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: Notification) {
            val context = rowNotificationsBinding.root.context
            val currentNotification = formatNotificationMessage(notification, context)
            rowNotificationsBinding.title.text = Html.fromHtml(currentNotification, Html.FROM_HTML_MODE_LEGACY)
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

        private fun formatNotificationMessage(notification: Notification, context: Context): String {
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
                    databaseService.withRealm { realm ->
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
                        "<b>${context.getString(R.string.join_request_prefix)}</b> " +
                            context.getString(R.string.user_requested_to_join_team, requesterName, teamName)
                    }
                }
                else -> notification.message
            }
        }

        private fun formatTaskNotification(context: Context, taskTitle: String, dateValue: String): String {
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
            }
        }
    }
}
