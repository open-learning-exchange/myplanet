package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.model.Notifications
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler

class SeeAllNotificationsFragment : Fragment() {

    private lateinit var notificationsAdapter: AdapterNotification
    private lateinit var notifications: MutableList<Notifications>
    private lateinit var btnMarkAllAsRead: Button
    private lateinit var mRealm: Realm

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_see_all_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mRealm = Realm.getDefaultInstance()
        notifications = mutableListOf()

        // Query the tasks from Realm
        val model = UserProfileDbHandler(requireContext()).userModel!!
        val tasks = mRealm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", model.id)
            .findAll()

        // Log the tasks to ensure they are being fetched
        tasks.forEach {
            Log.d("SeeAll", "Task: ${it.title} - ${it.description}")
        }

        // Add tasks to notifications list
        tasks.forEach {
            notifications.add(Notifications(R.drawable.task_pending, "${it.title} - ${it.description}"))
        }

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

        for (position in notifications.indices) {
            val isRead = notificationsAdapter.getReadStatus(position)
            notifications[position].isRead = isRead
        }

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

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }
}
