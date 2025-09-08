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
                "join_request" -> {
                    val joinRequestId = notification.relatedId
                    val teamName = databaseService.withRealm { realm ->
                        val joinRequest = realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", joinRequestId)
                            .equalTo("docType", "request")
                            .findFirst()
                        joinRequest?.let { jr ->
                            realm.where(RealmMyTeam::class.java)
                                .equalTo("_id", jr.teamId)
                                .findFirst()?.name
                        }
                    } ?: "Unknown Team"
                    val existing = notification.message
                    if (existing.isNotEmpty()) {
                        // Minimal parse: support English & Spanish stored forms
                        val regexes = listOf(
                            "^(.+) has requested to join (.+)$".toRegex(),
                            "^(.+) ha solicitado unirse a (.+)$".toRegex()
                        )
                        var rebuilt: String? = null
                        for (r in regexes) {
                            val m = r.find(existing)
                            if (m != null) {
                                val requester = m.groupValues[1].trim()
                                rebuilt = context.getString(R.string.user_requested_to_join_team, requester, teamName)
                                break
                            }
                        }
                        rebuilt ?: existing
                    } else {
                        context.getString(R.string.user_requested_to_join_team, "Someone", teamName)
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
