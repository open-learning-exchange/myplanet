package org.ole.planet.myplanet.ui.dashboard.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (RealmNotification) -> Unit,
) : ListAdapter<NotificationUiModel, AdapterNotification.ViewHolderNotifications>(
    DiffUtilExtensions.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.notification.id == newItem.notification.id },
        areContentsTheSame = { oldItem, newItem ->
            oldItem.notification.isRead == newItem.notification.isRead &&
                oldItem.displayText.toString() == newItem.displayText.toString()
        }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        val rowNotificationsBinding =
            RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        val notificationItem = getItem(position)
        holder.bind(notificationItem)
    }

    inner class ViewHolderNotifications(
        private val rowNotificationsBinding: RowNotificationsBinding,
    ) : RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notificationItem: NotificationUiModel) {
            val notification = notificationItem.notification
            rowNotificationsBinding.title.text = notificationItem.displayText
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
