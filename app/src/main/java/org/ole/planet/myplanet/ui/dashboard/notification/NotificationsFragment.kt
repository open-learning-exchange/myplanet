package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import io.realm.Sort
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.R.array.status_options
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import java.util.ArrayList

class NotificationsFragment : Fragment() {
    private lateinit var fragmentNotificationsBinding: FragmentNotificationsBinding
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNotificationsBinding = FragmentNotificationsBinding.inflate(inflater, container, false)
        databaseService = DatabaseService(requireActivity())
        mRealm = databaseService.realmInstance
        userId = arguments?.getString("userId") ?: ""

        val notifications = loadNotifications(userId, "all")

        val options = resources.getStringArray(status_options)
        val optionsList: MutableList<String?> = ArrayList(listOf(*options))
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, optionsList)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        fragmentNotificationsBinding.status.adapter = spinnerAdapter
        fragmentNotificationsBinding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                val filteredNotifications = loadNotifications(userId, selectedOption)
                adapter.updateNotifications(filteredNotifications)

                if (filteredNotifications.isEmpty()) {
                    fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
                } else {
                    fragmentNotificationsBinding.emptyData.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        if (notifications.isEmpty()) {
            fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
        }

        val filteredNotifications = notifications.filter { notification ->
             notification.message.isNotEmpty() && notification.message != "INVALID"
        }

        adapter = AdapterNotification(filteredNotifications,
            onMarkAsReadClick = { position ->
                markAsRead(position) },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
        fragmentNotificationsBinding.rvNotifications.adapter = adapter
        fragmentNotificationsBinding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        fragmentNotificationsBinding.btnMarkAllAsRead.setOnClickListener {
            markAllAsRead()
        }
        updateMarkAllAsReadButtonVisibility()
        return fragmentNotificationsBinding.root
    }

    private fun handleNotificationClick(notification: RealmNotification) {
        when (notification.type) {
            "storage" -> {
                val intent = Intent(ACTION_INTERNAL_STORAGE_SETTINGS)
                startActivity(intent)
            }
            "survey" -> {
                val currentStepExam = mRealm.where(RealmStepExam::class.java).equalTo("name", notification.relatedId)
                    .findFirst()
                if (context is OnHomeItemClickListener) {
                    AdapterMySubmission.openSurvey(context as OnHomeItemClickListener, currentStepExam?.id, false, false, "")
                }
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
                        val teamObject = mRealm.where(RealmMyTeam::class.java)?.equalTo("_id", teamId)?.findFirst()

                        val f = TeamDetailFragment.newInstance(
                            teamId = teamId,
                            teamName = teamObject?.name ?: "",
                            teamType = teamObject?.type ?: "",
                            isMyTeam = true,
                            navigateToPage = 3
                        )

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
        updateMarkAllAsReadButtonVisibility()
    }

    private fun markAllAsRead() {
        mRealm.executeTransaction { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .findAll()
                .forEach { it.isRead = true }
        }
        adapter.updateNotifications(loadNotifications(userId, fragmentNotificationsBinding.status.selectedItem.toString().lowercase()))
        updateMarkAllAsReadButtonVisibility()
        updateUnreadCount()
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        val unreadCount = getUnreadNotificationsSize()
        fragmentNotificationsBinding.btnMarkAllAsRead.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
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
        if (::mRealm.isInitialized) {
            mRealm.close()
        }
    }
}
