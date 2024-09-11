package org.ole.planet.myplanet.ui.dashboard

import android.text.SpannableString
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.ui.dashboard.AdapterNotifications.ViewHolderNotifications

class AdapterNotifications(private val notificationList: List<SpannableString>) : RecyclerView.Adapter<ViewHolderNotifications>() {
    private lateinit var rowNotificationsBinding: RowNotificationsBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderNotifications {
        rowNotificationsBinding = RowNotificationsBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderNotifications(rowNotificationsBinding)
    }
    override fun onBindViewHolder(holder: ViewHolderNotifications, position: Int) {
        rowNotificationsBinding.title.text = notificationList[position]
    }

    override fun getItemCount(): Int {
        return notificationList.size
    }

    class ViewHolderNotifications(rowNotificationsBinding: RowNotificationsBinding) : RecyclerView.ViewHolder(rowNotificationsBinding.root)
}