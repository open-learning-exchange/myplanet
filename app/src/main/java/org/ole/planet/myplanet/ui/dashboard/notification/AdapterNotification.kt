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
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    private val databaseService: DatabaseService,
    notifications: List<RealmNotification>,
    private val onMarkAsReadClick: (Int) -> Unit,
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
        holder.bind(notification, position)
    }

    fun updateNotifications(newNotifications: List<RealmNotification>) {
        submitList(newNotifications)
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: RealmNotification, position: Int) {
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
                    onMarkAsReadClick(position)
                }
            }

            rowNotificationsBinding.root.setOnClickListener {
                onNotificationClick(notification)
            }
        }

        private fun formatNotificationMessage(notification: RealmNotification, context: Context): String {
            return when (notification.type.lowercase()) {
                "survey" -> context.getString(R.string.pending_survey_notification) + " ${notification.message}"
                "task" -> {
                    val datePattern = Pattern.compile("\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s\\d{1,2},\\s\\w+\\s\\d{4}\\b")
                    val matcher = datePattern.matcher(notification.message)

                    if (matcher.find()) {
                        val taskTitle = notification.message.substring(0, matcher.start()).trim()
                        val dateValue = notification.message.substring(matcher.start()).trim()
                        return formatTaskNotification(context, taskTitle, dateValue)
                    } else {
                        "INVALID"
                    }
                }
                "resource" -> {
                    val resourceCount = notification.message.toIntOrNull()
                    resourceCount?.let {
                        context.getString(R.string.resource_notification, it)
                    } ?: "INVALID"
                }
                "storage" -> {
                    val storageValue = notification.message.replace("%", "").toIntOrNull()
                    storageValue?.let {
                        when {
                            it <= 10 -> context.getString(R.string.storage_running_low) + " ${it}%"
                            it <= 40 -> context.getString(R.string.storage_running_low) + " ${it}%"
                            else -> context.getString(R.string.storage_available) + " ${it}%"
                        }
                    } ?: "INVALID"
                }
                "join_request" -> {
                    val teamId = notification.relatedId
                    val teamName = databaseService.withRealm { realm ->
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", teamId)
                            .findFirst()?.name
                    } ?: "Unknown Team"
                    
                    val message = notification.message
                    if (message.isNotEmpty()) {
                        // Try to parse the stored message to extract user name for re-translation
                        val parsedMessage = parseJoinRequestMessage(message, teamName, context)
                        "<b>${context.getString(R.string.join_request_prefix)}</b> $parsedMessage"
                    } else {
                        "<b>${context.getString(R.string.join_request_prefix)}</b> ${context.getString(R.string.new_request_to_join, teamName)}"
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
        
        private fun parseJoinRequestMessage(message: String, teamName: String, context: Context): String {
            // Try to extract username from patterns like "username has requested to join teamname"
            val patterns = listOf(
                "(.+) has requested to join (.+)".toRegex(),
                "(.+) ha solicitado unirse a (.+)".toRegex()
            )
            
            for (pattern in patterns) {
                val match = pattern.find(message)
                if (match != null) {
                    val username = match.groupValues[1].trim()
                    return context.getString(R.string.user_requested_to_join_team, username, teamName)
                }
            }
            
            // If we can't parse it, return the original message
            return message
        }
    }
}}
