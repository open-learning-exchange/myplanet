package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.databinding.RowNotificationBinding
import org.ole.planet.myplanet.model.Notifications

class AdapterNotification(var context: Context, var list: List<Notifications>, var callback: NotificationCallback) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowNotificationBinding: RowNotificationBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowNotificationBinding = RowNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderNotification(rowNotificationBinding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNotification) {
            rowNotificationBinding.title.text = list[position].text
            rowNotificationBinding.icon.setImageResource(list[position].icon)
            holder.itemView.setOnClickListener {
                when (position) {
                    0 -> callback.showResourceDownloadDialog()
                    1 -> callback.showUserResourceDialog()
                    2 -> callback.showPendingSurveyDialog()
                    3 -> callback.forceDownloadNewsImages()
                    4 -> callback.downloadDictionary()
                    5 -> callback.showTaskListDialog()
                    7 -> callback.syncKeyId()
                }
            }
        }
    }

    class ViewHolderNotification(rowNotificationBinding: RowNotificationBinding) : RecyclerView.ViewHolder(rowNotificationBinding.root) {
        private var rowNotificationBinding: RowNotificationBinding

        init {
            this.rowNotificationBinding = rowNotificationBinding
        }
    }
}