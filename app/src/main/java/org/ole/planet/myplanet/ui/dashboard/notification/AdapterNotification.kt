package org.ole.planet.myplanet.ui.dashboard.notification

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.dto.NotificationItem
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterNotification(
    notifications: List<NotificationItem>,
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (NotificationItem) -> Unit,
) : ListAdapter<NotificationItem, AdapterNotification.ViewHolderNotifications>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    init {
        submitList(notifications)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        val rowNotificationsBinding =
            RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    fun updateNotifications(newNotifications: List<NotificationItem>) {
        submitList(newNotifications)
    }

    inner class ViewHolderNotifications(
        private val rowNotificationsBinding: RowNotificationsBinding
    ) : RecyclerView.ViewHolder(rowNotificationsBinding.root) {
        fun bind(notification: NotificationItem) {
            rowNotificationsBinding.title.text =
                Html.fromHtml(notification.message, Html.FROM_HTML_MODE_LEGACY)

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
    }
}
