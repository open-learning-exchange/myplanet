package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.model.Notifications

class AdapterNotification(private val notifications: List<Notifications>) :
    RecyclerView.Adapter<AdapterNotification.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    override fun getItemCount(): Int = notifications.size

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.title)

        fun bind(notification: Notifications) {
            val customMessage = when (notification.source) {
                "resource" -> "New resource ${notification.title} has been added"
                "course" -> "New course ${notification.title} has been added to team"
                else -> "New notification: ${notification.title}"
            }
            messageTextView.text = customMessage
        }
    }
}


//class AdapterNotification(private val notificationList: MutableList<Notifications>, private val callback: NotificationCallback, private val showMarkAsReadButton: Boolean, private val showImages: Boolean) : RecyclerView.Adapter<AdapterNotification.ViewHolderNotification>() {
//
//    private val sharedPrefs = context.getSharedPreferences("notifications_prefs", Context.MODE_PRIVATE)
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotification {
//        val inflater = LayoutInflater.from(parent.context)
//        val view = inflater.inflate(R.layout.row_notification, parent, false)
//        return ViewHolderNotification(view)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolderNotification, position: Int) {
//        holder.bind(notificationList[position], showImages)
//    }
//
//    override fun getItemCount(): Int {
//        return notificationList.size
//    }
//
//    inner class ViewHolderNotification(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        private val title: TextView = itemView.findViewById(R.id.title)
//        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
//        private val btnMarkAsRead: Button = itemView.findViewById(R.id.btn_mark_as_read)
//        private val icon: ImageView = itemView.findViewById(R.id.icon)
//
//        fun bind(notification: Notifications, showImages: Boolean) {
//            title.text = notification.text
//            timestamp.visibility = View.GONE
//            btnMarkAsRead.visibility = if (showMarkAsReadButton) View.VISIBLE else View.GONE
//
//            if (showImages) {
//                icon.visibility = View.VISIBLE
//                icon.setImageResource(notification.icon)
//            } else {
//                icon.visibility = View.GONE
//            }
//
//            btnMarkAsRead.setOnClickListener {
//                markAsRead(notification.id)
//            }
//
//            itemView.setOnClickListener {
//                when (absoluteAdapterPosition) {
//                    0 -> callback.showResourceDownloadDialog()
//                    1 -> callback.showUserResourceDialog()
//                    2 -> callback.showPendingSurveyDialog()
//                    3 -> callback.forceDownloadNewsImages()
//                    4 -> callback.downloadDictionary()
//                    5 -> callback.showTaskListDialog()
//                }
//            }
//        }
//    }
//
//    private fun markAsRead(notificationId: Int) {
//        sharedPrefs.edit().putBoolean("notification_$notificationId", true).apply()
//
//        val position = notificationList.indexOfFirst { it.id == notificationId }
//        if (position != -1) {
//            notificationList.removeAt(position)
//            notifyItemRemoved(position)
//        }
//    }
//
//    fun markAllAsRead() {
//        notificationList.forEach {
//            sharedPrefs.edit().putBoolean("notification_${it.id}", true).apply()
//        }
//        notificationList.clear()
//        notifyDataSetChanged()
//    }
//}
