package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import java.util.Date
import java.util.UUID

open class RealmNotification : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNotificationsBinding = FragmentNotificationsBinding.inflate(inflater, container, false)
        databaseService = DatabaseService(requireActivity())
        mRealm = databaseService.realmInstance

        val notifications = loadNotifications()

        if (notifications.isEmpty()) {
            fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
        }

        adapter = AdapterNotifications(notifications) { position ->
            markAsRead(position)
        }
        fragmentNotificationsBinding.rvNotifications.adapter = adapter
        fragmentNotificationsBinding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        return fragmentNotificationsBinding.root
    }

    private fun loadNotifications(): List<RealmNotification> {
        return mRealm.where(RealmNotification::class.java)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }
}