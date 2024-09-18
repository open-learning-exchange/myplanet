package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import io.realm.Sort
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentNotificationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment

class NotificationFragment : Fragment() {
    private lateinit var fragmentNotificationBinding: FragmentNotificationBinding
    private lateinit var databaseService: DatabaseService
    private lateinit var mRealm: Realm
    private lateinit var adapter: AdapterNotification
    private lateinit var userId: String
    private var notificationUpdateListener: NotificationListener? = null
    private lateinit var dashboardActivity: DashboardActivity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DashboardActivity) {
            dashboardActivity = context
        }
    }

    fun setNotificationUpdateListener(listener: NotificationListener) {
        this.notificationUpdateListener = listener
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNotificationBinding = FragmentNotificationBinding.inflate(inflater, container, false)
        databaseService = DatabaseService(requireActivity())
        mRealm = databaseService.realmInstance
        userId = arguments?.getString("userId") ?: ""

        val notifications = loadNotifications(userId, "all")

        val spinnerAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.status_options, R.layout.spinner_item)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        fragmentNotificationBinding.status?.adapter = spinnerAdapter
        fragmentNotificationBinding.status?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                val filteredNotifications = loadNotifications(userId, selectedOption)
                adapter.updateNotifications(filteredNotifications)

                if (filteredNotifications.isEmpty()) {
                    fragmentNotificationBinding.emptyData?.visibility = View.VISIBLE
                } else {
                    fragmentNotificationBinding.emptyData?.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        if (notifications.isEmpty()) {
            fragmentNotificationBinding.emptyData?.visibility = View.VISIBLE
        }

        adapter = AdapterNotification(notifications,
            onMarkAsReadClick = { position ->
                markAsRead(position) },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
        fragmentNotificationBinding.rvNotifications.adapter = adapter
        fragmentNotificationBinding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        fragmentNotificationBinding.btnMarkAllAsRead?.setOnClickListener {
            markAllAsRead()
        }

        return fragmentNotificationBinding.root
    }

    private fun handleNotificationClick(notification: RealmNotification) {
        when (notification.type) {
            "storage" -> {
                Log.d("ole2", "storage clicked")
            }
            "survey" -> {
                Log.d("ole2", "survey clicked")
            }
            "task" -> {
                val taskId = notification.relatedId
                val task = mRealm.where(RealmTeamTask::class.java)
                    .equalTo("id", taskId)
                    .findFirst()

                val linkJson = JSONObject(task?.link ?: "{}")
                val teamId = linkJson.optString("teams")
                if (teamId.isNotEmpty()) {
                    if (context is OnHomeItemClickListener) {
                        val f = TeamDetailFragment()
                        val b = Bundle()
                        b.putString("id", teamId)
                        b.putBoolean("isMyTeam", true)
                        b.putInt("navigateToPage", 3)
                        f.arguments = b
                        (context as OnHomeItemClickListener).openCallFragment(f)
                    }
                }
            }
            "resource" -> {
                dashboardActivity.openMyFragment(ResourcesFragment())
            }
        }

        if (!notification.isRead) {
            markAsRead(adapter.notificationList.indexOf(notification))
        }
    }

    private fun loadNotifications(userId: String, filter: String): List<RealmNotification> {
        val query = mRealm.where(RealmNotification::class.java)
            .equalTo("userId", userId)

        when (filter) {
            "read" -> query.equalTo("isRead", true)
            "unread" -> query.equalTo("isRead", false)
            "all" -> {}
        }

        return query.sort("createdAt", Sort.DESCENDING).findAll().toList()
    }

    private fun markAsRead(position: Int) {
        val notification = adapter.notificationList[position]
        mRealm.executeTransaction {
            notification.isRead = true
        }
        adapter.notifyItemChanged(position)
        updateUnreadCount()
    }

    private fun markAllAsRead() {
        mRealm.executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .findAll()
                .forEach { it.isRead = true }
        }
        adapter.updateNotifications(loadNotifications(userId, fragmentNotificationBinding.status?.selectedItem.toString().lowercase()))
        updateMarkAllAsReadButtonVisibility()
        updateUnreadCount()
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        val unreadCount = getUnreadNotificationsSize()
        fragmentNotificationBinding.btnMarkAllAsRead?.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    private fun getUnreadNotificationsSize(): Int {
        return mRealm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("isRead", false)
            .count()
            .toInt()
    }

    private fun updateUnreadCount() {
        val unreadCount = getUnreadNotificationsSize()
        notificationUpdateListener?.onNotificationCountUpdated(unreadCount)
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }
}
