package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.model.Notifications


class AdapterNotification(var context: Context, var list: List<Notifications>, var callback: NotificationCallback) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        var v = LayoutInflater.from(context).inflate(R.layout.row_notification, parent, false)
        return ViewHolderNotification(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderNotification) {
            holder.title.text = list[position].text
            holder.icon.setImageResource(list[position].icon)
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


    internal inner class ViewHolderNotification(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: TextView = itemView.findViewById(R.id.title)
        var icon: ImageView = itemView.findViewById(R.id.icon)
    }


}