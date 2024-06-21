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
    private val callback: NotificationCallback
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
            binding.btnMarkAsRead.visibility = if (notification.isRead) View.GONE else View.VISIBLE

            binding.btnMarkAsRead.setOnClickListener {
                markAsRead(adapterPosition)
            }

            itemView.setOnClickListener {
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
        notificationList[position].isRead = true
        notifyItemChanged(position)
    }

    fun markAllAsRead() {
        notificationList.forEach { it.isRead = true }
        notifyDataSetChanged()
    }
}
