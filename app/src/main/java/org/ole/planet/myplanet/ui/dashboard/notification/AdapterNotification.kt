package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification
import java.util.regex.Pattern

class AdapterNotification(
    var notificationList: List<RealmNotification>,
    private val onMarkAsReadClick: (Int) -> Unit,
    private val onNotificationClick: (RealmNotification) -> Unit
) : RecyclerView.Adapter<AdapterNotification.ViewHolderNotifications>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        val rowNotificationsBinding = RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        val notification = notificationList[position]
        holder.bind(notification, position)
    }

    override fun getItemCount(): Int = notificationList.size

    fun updateNotifications(newNotifications: List<RealmNotification>) {
        notificationList = newNotifications
        notifyDataSetChanged()
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: RealmNotification, position: Int) {
            val context = rowNotificationsBinding.root.context
            val currentNotification = formatNotificationMessage(notification, context)
            rowNotificationsBinding.title.text = currentNotification
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
                        context.getString(R.string.task_notification, taskTitle, dateValue)
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
                    val storageValue = notification.message.toIntOrNull()
                    storageValue?.let {
                        when {
                            it <= 10 -> context.getString(R.string.storage_running_low) +" ${it}%"
                            it <= 40 -> context.getString(R.string.storage_running_low)+ " ${it}%"
                            else -> context.getString(R.string.storage_available)+ " ${it}%"
                        }
                    } ?: "INVALID"
                }
                else -> "INVALID"
            }
        }
    }
}
