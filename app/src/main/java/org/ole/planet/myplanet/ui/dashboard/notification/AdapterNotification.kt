package org.ole.planet.myplanet.ui.dashboard.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
            rowNotificationsBinding.title.text = notification.message
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
    }
}
