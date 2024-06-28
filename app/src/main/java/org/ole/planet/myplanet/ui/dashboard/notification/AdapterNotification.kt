package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.model.Notifications

class AdapterNotification(
    private val context: Context,
    private val notificationList: MutableList<Notifications>,
    private val callback: NotificationCallback,
    private val showMarkAsReadButton: Boolean,
    private val showImages: Boolean // Add this flag
) : RecyclerView.Adapter<AdapterNotification.ViewHolderNotification>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotification {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.row_notification, parent, false)
        return ViewHolderNotification(view)
    }

    override fun onBindViewHolder(holder: ViewHolderNotification, position: Int) {
        holder.bind(notificationList[position], showImages)
    }

    override fun getItemCount(): Int {
        return notificationList.size
    }

    inner class ViewHolderNotification(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val btnMarkAsRead: Button = itemView.findViewById(R.id.btn_mark_as_read)
        private val icon: ImageView = itemView.findViewById(R.id.icon)  // Reference to ImageView

        fun bind(notification: Notifications, showImages: Boolean) {
            title.text = notification.text
            timestamp.visibility = View.GONE // You can set the timestamp if available
            btnMarkAsRead.visibility = if (showMarkAsReadButton) View.VISIBLE else View.GONE

            // Conditionally show or hide the icon
            if (showImages) {
                icon.visibility = View.VISIBLE
                icon.setImageResource(notification.icon)
            } else {
                icon.visibility = View.GONE
            }

            btnMarkAsRead.setOnClickListener {
                markAsRead(bindingAdapterPosition)
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
        notificationList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun markAllAsRead() {
        notificationList.clear()
        notifyDataSetChanged()
    }
}
