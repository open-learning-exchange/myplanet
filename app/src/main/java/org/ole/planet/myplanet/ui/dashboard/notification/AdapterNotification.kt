package org.ole.planet.myplanet.ui.dashboard.notification

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification

class AdapterNotification(var notificationList: List<RealmNotification>, private val onMarkAsReadClick: (Int) -> Unit, private val onNotificationClick: (RealmNotification) -> Unit) : RecyclerView.Adapter<AdapterNotification.ViewHolderNotifications>() {
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

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) : RecyclerView.ViewHolder(rowNotificationsBinding.root) {
        fun bind(notification: RealmNotification, position: Int) {
            val currentNotification= formatNotificationMessage(notification)
            Log.d("noti","type: ${notification.type} + notification : $currentNotification")
            rowNotificationsBinding.title.text =currentNotification
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

        fun formatNotificationMessage(notification: RealmNotification): String {
            return when (notification.type.lowercase()) {
                "survey" -> "${context.getString(R.string.pending_survey_notification)} ${notification.message}"
                "task" -> {
                    val parts = notification.message.split(" ")
                    if (parts.size >= 2) {
                        val taskTitle = parts[0]
                        val dateValue = parts.subList(1, parts.size).joinToString(" ")
                        context.getString(R.string.task_notification, taskTitle, dateValue)
                    } else {
                        "Invalid task message format"
                    }
                }
                "resource" -> {
                    val resourceCount = notification.message.toIntOrNull()
                    if (resourceCount != null) {
                       context.getString(R.string.resource_notification, resourceCount)
                    } else {
                        "Invalid resource count"
                    }
                }
                "storage" -> {
                    val storageValue = notification.message.toIntOrNull()
                    if (storageValue != null) {
                        when {
                            storageValue <= 10 -> "${context.getString(R.string.storage_running_low)} $storageValue% ${context.getString(R.string.available)}"
                            storageValue <= 40 -> "${context.getString(R.string.storage_running_low)} $storageValue% ${context.getString(R.string.available)}"
                            else -> "Storage $storageValue is sufficient"
                        }
                    } else {
                        "Invalid storage value"
                    }
                }
                else -> "Unknown notification type"
            }
        }

    }
}
