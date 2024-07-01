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

    private lateinit var mRealm: Realm
    private lateinit var notificationsAdapter: AdapterNotification

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_see_all_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mRealm = Realm.getDefaultInstance()
        val model = UserProfileDbHandler(requireContext()).userModel!!

        val tasks = mRealm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", model.id)
            .findAll()

        val sharedPrefs = requireContext().getSharedPreferences("notifications_prefs", Context.MODE_PRIVATE)

        val notifications = tasks.map {
            Notifications(
                id = it.id,
                icon = R.drawable.task_pending,
                text = "You were assigned a new task: " + "${it.title}"
            )
        }.filterNot {
            sharedPrefs.getBoolean("notification_${it.id}", false)
        }.toMutableList()

        Log.d("Notifications", "Loaded notifications: $notifications")

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view_notifications)
        recyclerView.layoutManager = LinearLayoutManager(context)
        notificationsAdapter = AdapterNotification(requireContext(), notifications, object : NotificationCallback {
            override fun showResourceDownloadDialog() {}
            override fun showUserResourceDialog() {}
            override fun showPendingSurveyDialog() {}
            override fun forceDownloadNewsImages() {}
            override fun downloadDictionary() {}
            override fun showTaskListDialog() {}
            override fun syncKeyId() {}
        }, showMarkAsReadButton = true, showImages = false)

        recyclerView.adapter = notificationsAdapter

        val btnMarkAllAsRead: Button = view.findViewById(R.id.btn_mark_all_as_read)
        btnMarkAllAsRead.setOnClickListener {
            notificationsAdapter.markAllAsRead()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }
}
