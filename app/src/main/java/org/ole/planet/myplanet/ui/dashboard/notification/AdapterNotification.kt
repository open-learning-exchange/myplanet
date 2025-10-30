package org.ole.planet.myplanet.ui.dashboard.notification

import android.os.StrictMode
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.utilities.DiffUtils as DiffUtilExtensions

class AdapterNotification(
    notifications: List<NotificationDisplayItem>,
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (NotificationDisplayItem) -> Unit
) : ListAdapter<NotificationDisplayItem, AdapterNotification.ViewHolderNotifications>(
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

    fun updateNotifications(newNotifications: List<NotificationDisplayItem>) {
        submitList(newNotifications)
    }

    inner class ViewHolderNotifications(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: NotificationDisplayItem) {
            if (BuildConfig.DEBUG) {
                val originalPolicy = StrictMode.getThreadPolicy()
                val strictPolicy = StrictMode.ThreadPolicy.Builder(originalPolicy)
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
                StrictMode.setThreadPolicy(strictPolicy)
                try {
                    renderNotification(notification)
                } finally {
                    StrictMode.setThreadPolicy(originalPolicy)
                }
            } else {
                renderNotification(notification)
            }
        }

        private fun renderNotification(notification: NotificationDisplayItem) {
            rowNotificationsBinding.title.text = Html.fromHtml(notification.displayHtml, Html.FROM_HTML_MODE_LEGACY)
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
