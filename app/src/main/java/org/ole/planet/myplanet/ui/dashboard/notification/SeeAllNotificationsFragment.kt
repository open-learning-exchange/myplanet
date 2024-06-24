package org.ole.planet.myplanet.ui.dashboard.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.model.Notifications

class SeeAllNotificationsFragment : Fragment() {

    private lateinit var notificationsAdapter: AdapterNotification
    private lateinit var notifications: MutableList<Notifications>
    private lateinit var btnMarkAllAsRead: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_see_all_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notifications = mutableListOf(
            Notifications(R.drawable.notifications, "Admin okuro has posted a message on \"Gideon Team\" team."),
            Notifications(R.drawable.notifications, "You were assigned a new role")
        )

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view_notifications)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Instantiate AdapterNotification with showMarkAsReadButton = true
        notificationsAdapter = AdapterNotification(requireContext(), notifications, object : NotificationCallback {
            override fun showResourceDownloadDialog() {}
            override fun showUserResourceDialog() {}
            override fun showPendingSurveyDialog() {}
            override fun forceDownloadNewsImages() {}
            override fun downloadDictionary() {}
            override fun showTaskListDialog() {}
            override fun syncKeyId() {}
        }, showMarkAsReadButton = true)

        recyclerView.adapter = notificationsAdapter

        btnMarkAllAsRead = view.findViewById(R.id.btn_mark_all_as_read)
        updateMarkAllButtonState()

        btnMarkAllAsRead.setOnClickListener {
            notificationsAdapter.markAllAsRead()
            updateMarkAllButtonState()
        }
    }

    private fun updateMarkAllButtonState() {
        val allRead = notifications.all { it.isRead }
        btnMarkAllAsRead.isEnabled = !allRead
        btnMarkAllAsRead.alpha = if (allRead) 0.5f else 1.0f
    }
}
