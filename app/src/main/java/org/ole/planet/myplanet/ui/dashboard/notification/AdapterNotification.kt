package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.databinding.RowNotificationBinding
import org.ole.planet.myplanet.model.Notifications

class AdapterNotification(
    private val context: Context,
    private val notificationList: MutableList<Notifications>,
    private val callback: NotificationCallback,
    private val showMarkAsReadButton: Boolean = false
) : RecyclerView.Adapter<AdapterNotification.ViewHolderNotification>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotification {
        val binding = RowNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotification(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderNotification, position: Int) {
        holder.bind(notificationList[position])
    }

    override fun getItemCount(): Int {
        return notificationList.size
    }

    inner class ViewHolderNotification(private val binding: RowNotificationBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notifications) {
            binding.title.text = notification.text
            binding.timestamp.text = notification.timestamp
            binding.icon.setImageResource(notification.icon)
            binding.title.setTextColor(if (notification.isRead) ContextCompat.getColor(context, R.color.md_black_1000) else ContextCompat.getColor(context, R.color.md_blue_500))

            binding.btnMarkAsRead.visibility = if (showMarkAsReadButton && !notification.isRead) View.VISIBLE else View.GONE

            binding.btnMarkAsRead.setOnClickListener {
                markAsRead(bindingAdapterPosition)
            }

            itemView.setOnClickListener {
                markAsRead(bindingAdapterPosition)
                when (absoluteAdapterPosition) {
                    0 -> callback.showResourceDownloadDialog()
                    1 -> callback.showUserResourceDialog()
                    2 -> callback.showPendingSurveyDialog()
                    3 -> callback.forceDownloadNewsImages()
                    4 -> callback.downloadDictionary()
                    5 -> callback.showTaskListDialog()
                }
            }
        }
    }

    private fun markAsRead(position: Int) {
        val notification = notificationList[position]
        if (!notification.isRead) {
            notification.isRead = true
            saveReadStatus(position, true) // Save the read status using position
            notifyItemChanged(position)
        }
    }

    fun markAllAsRead() {
        notificationList.forEach { it.isRead = true }
        notifyDataSetChanged()
    }

    private fun saveReadStatus(position: Int, isRead: Boolean) {
        val sharedPreferences = context.getSharedPreferences("notifications_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("notification_$position", isRead).apply()
    }

    fun getReadStatus(position: Int): Boolean {
        val sharedPreferences = context.getSharedPreferences("notifications_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("notification_$position", false)
    }
}
