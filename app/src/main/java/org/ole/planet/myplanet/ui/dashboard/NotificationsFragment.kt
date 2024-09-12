package org.ole.planet.myplanet.ui.dashboard

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
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationListener
import java.util.Date
import java.util.UUID

open class RealmNotification : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String = "" // e.g., "resource", "survey", "task", "storage"
    var relatedId: String? = null // e.g., task ID, survey ID, etc.
}

class NotificationsFragment : Fragment() {
    private lateinit var fragmentNotificationsBinding: FragmentNotificationsBinding
    private lateinit var databaseService: DatabaseService
    private lateinit var mRealm: Realm
    private lateinit var adapter: AdapterNotifications
    private lateinit var userId: String
    private var notificationUpdateListener: NotificationListener? = null

    fun setNotificationUpdateListener(listener: NotificationListener) {
        this.notificationUpdateListener = listener
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNotificationsBinding = FragmentNotificationsBinding.inflate(inflater, container, false)
        databaseService = DatabaseService(requireActivity())
        mRealm = databaseService.realmInstance
        userId = arguments?.getString("userId") ?: ""


        val notifications = loadNotifications(userId, "all")

        val spinnerAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.status_options, android.R.layout.simple_spinner_item)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fragmentNotificationsBinding.status.adapter = spinnerAdapter
        fragmentNotificationsBinding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                Log.d("NotificationsFragment", "Selected option: $selectedOption")

                // Reload notifications based on the selected option
                val filteredNotifications = loadNotifications(userId, selectedOption)
                adapter.updateNotifications(filteredNotifications)

                if (filteredNotifications.isEmpty()) {
                    fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
                } else {
                    fragmentNotificationsBinding.emptyData.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle case where nothing is selected
            }
        }

        if (notifications.isEmpty()) {
            fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
        }

        adapter = AdapterNotifications(notifications) { position ->
            markAsRead(position)
        }
        fragmentNotificationsBinding.rvNotifications.adapter = adapter
        fragmentNotificationsBinding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        fragmentNotificationsBinding.btnMarkAllAsRead.setOnClickListener {
            markAllAsRead()
        }

        return fragmentNotificationsBinding.root
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

    private fun loadNotifications(userId: String): List<RealmNotification> {
        return mRealm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .sort("createdAt", Sort.DESCENDING)
            .findAll()
            .toList()
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
        mRealm.close()
    }
}