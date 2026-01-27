package org.ole.planet.myplanet.ui.dashboard.notifications

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.utils.DiffUtils

class NotificationsAdapter(
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationsAdapter.NotificationsViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationsViewHolder {
        val rowNotificationsBinding =
            RowNotificationsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationsViewHolder(rowNotificationsBinding)
    }

    override fun onBindViewHolder(holder: NotificationsViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    inner class NotificationsViewHolder(private val rowNotificationsBinding: RowNotificationsBinding) :
        RecyclerView.ViewHolder(rowNotificationsBinding.root) {

        fun bind(notification: Notification) {
            rowNotificationsBinding.title.text =
                Html.fromHtml(
                    notification.formattedText.toString(),
                    Html.FROM_HTML_MODE_LEGACY
                )
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
