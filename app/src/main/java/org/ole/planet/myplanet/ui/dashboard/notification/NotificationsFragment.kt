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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import java.util.ArrayList
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.team.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.utilities.NotificationUtils

@AndroidEntryPoint
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var notificationRepository: NotificationRepository
    private lateinit var adapter: AdapterNotification
    private lateinit var userId: String
    private var notificationUpdateListener: NotificationListener? = null
    private lateinit var dashboardActivity: DashboardActivity
    private var unreadCountCache: Int = 0

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
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        userId = arguments?.getString("userId") ?: ""

        val notifications = loadNotifications(userId, "all")

        val options = resources.getStringArray(status_options)
        val optionsList: MutableList<String?> = ArrayList(listOf(*options))
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, optionsList)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.status.adapter = spinnerAdapter
        binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                val filteredNotifications = loadNotifications(userId, selectedOption)
                adapter.updateNotifications(filteredNotifications)

                binding.emptyData.visibility = if (filteredNotifications.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        if (notifications.isEmpty()) {
            binding.emptyData.visibility = View.VISIBLE
        }

        refreshUnreadCountCache()

        adapter = AdapterNotification(
            databaseService,
            notifications,
            onMarkAsReadClick = { notificationId ->
                markAsReadById(notificationId)
            },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        binding.btnMarkAllAsRead.setOnClickListener {
            markAllAsRead()
        }
        updateMarkAllAsReadButtonVisibility()
        updateUnreadCount()
        return binding.root
    }

    private fun handleNotificationClick(notification: RealmNotification) {
        when (notification.type) {
            "storage" -> {
                val intent = Intent(ACTION_INTERNAL_STORAGE_SETTINGS)
                startActivity(intent)
            }
            "survey" -> {
                databaseService.withRealm { realm ->
                    val currentStepExam = realm.where(RealmStepExam::class.java)
                        .equalTo("name", notification.relatedId)
                        .findFirst()
                    if (currentStepExam != null && activity is OnHomeItemClickListener) {
                        AdapterMySubmission.openSurvey(
                            activity as OnHomeItemClickListener,
                            currentStepExam.id,
                            false,
                            false,
                            "",
                        )
                    }
                }
            }
            "task" -> {
                databaseService.withRealm { realm ->
                    val taskId = notification.relatedId
                    val task = realm.where(RealmTeamTask::class.java)
                        .equalTo("id", taskId)
                        .findFirst()

                    val linkJson = JSONObject(task?.link ?: "{}")
                    val teamId = linkJson.optString("teams")
                    if (teamId.isNotEmpty() && activity is OnHomeItemClickListener) {
                        val teamObject = realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", teamId)
                            .findFirst()
                        val f = TeamDetailFragment.newInstance(
                            teamId = teamId,
                            teamName = teamObject?.name ?: "",
                            teamType = teamObject?.type ?: "",
                            isMyTeam = true,
                            navigateToPage = TasksPage,
                        )

                        (activity as OnHomeItemClickListener).openCallFragment(f)
                    }
                }
            }
            "join_request" -> {
                val joinRequestId = notification.relatedId
                if (joinRequestId?.isNotEmpty() == true && activity is OnHomeItemClickListener) {
                    val actualJoinRequestId = if (joinRequestId.startsWith("join_request_")) {
                        joinRequestId.removePrefix("join_request_")
                    } else {
                        joinRequestId
                    }
                    databaseService.withRealm { realm ->
                        val joinRequest = realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", actualJoinRequestId)
                            .equalTo("docType", "request")
                            .findFirst()

                        val teamId = joinRequest?.teamId
                        if (teamId?.isNotEmpty() == true) {
                            val f = TeamDetailFragment()
                            val b = Bundle()
                            b.putString("id", teamId)
                            b.putBoolean("isMyTeam", true)
                            b.putString("navigateToPage", JoinRequestsPage.id)
                            f.arguments = b
                            (activity as OnHomeItemClickListener).openCallFragment(f)
                        }
                    }
                }
            }
            "resource" -> {
                dashboardActivity.openMyFragment(ResourcesFragment())
            }
        }

        if (!notification.isRead) {
            markAsReadById(notification.id)
        }
    }

    private fun loadNotifications(userId: String, filter: String): List<RealmNotification> {
        return databaseService.withRealm { realm ->
            val query = realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)

            when (filter) {
                "read" -> query.equalTo("isRead", true)
                "unread" -> query.equalTo("isRead", false)
                "all" -> {}
            }

            val results = query
                .sort("isRead", Sort.ASCENDING, "createdAt", Sort.DESCENDING)
                .findAll()
            realm.copyFromRealm(results).filter {
                it.message.isNotEmpty() && it.message != "INVALID"
            }
        }
    }

    private fun markAsReadById(notificationId: String) {
        markNotificationsAsRead(setOf(notificationId), isMarkAll = false) {
            notificationRepository.markNotificationsAsRead(setOf(notificationId))
        }
    }

    private fun markAllAsRead() {
        val notificationIds = adapter.currentList.map { it.id }.toSet()
        markNotificationsAsRead(notificationIds, isMarkAll = true) {
            notificationRepository.markAllUnreadAsRead(userId)
        }
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        _binding?.let { binding ->
            binding.btnMarkAllAsRead.visibility = if (unreadCountCache > 0) View.VISIBLE else View.GONE
        }
    }

    private fun getUnreadNotificationsSize(): Int {
        return databaseService.withRealm { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }

    private fun updateUnreadCount() {
        notificationUpdateListener?.onNotificationCountUpdated(unreadCountCache)
    }

    fun refreshNotificationsList() {
        if (::adapter.isInitialized && _binding != null) {
            val selectedFilter = binding.status.selectedItem.toString().lowercase()
            val notifications = loadNotifications(userId, selectedFilter)
            adapter.updateNotifications(notifications)
            refreshUnreadCountCache()
            updateMarkAllAsReadButtonVisibility()
            updateUnreadCount()

            binding.emptyData.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun refreshUnreadCountCache() {
        unreadCountCache = getUnreadNotificationsSize()
    }

    private fun markNotificationsAsRead(
        notificationIdsForUi: Set<String>,
        isMarkAll: Boolean,
        backgroundAction: suspend () -> Set<String>,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val binding = _binding ?: return@launch
            val selectedFilter = binding.status.selectedItem.toString().lowercase()
            val previousList = adapter.currentList.toList()
            val previousUnreadCount = unreadCountCache
            val appContext = requireContext().applicationContext

            val updatedList = if (notificationIdsForUi.isNotEmpty()) {
                getUpdatedListAfterMarkingRead(previousList, notificationIdsForUi, selectedFilter)
            } else {
                previousList
            }

            if (notificationIdsForUi.isNotEmpty()) {
                adapter.submitList(updatedList)
                binding.emptyData.visibility = if (updatedList.isEmpty()) View.VISIBLE else View.GONE
            }

            val unreadMarkedCount = if (isMarkAll) {
                previousUnreadCount
            } else {
                previousList.count { notificationIdsForUi.contains(it.id) && !it.isRead }
            }

            unreadCountCache = if (isMarkAll) {
                0
            } else {
                (previousUnreadCount - unreadMarkedCount).coerceAtLeast(0)
            }
            updateMarkAllAsReadButtonVisibility()
            updateUnreadCount()

            try {
                withContext(Dispatchers.IO) {
                    val idsToClear = backgroundAction()
                    val notificationManager = NotificationUtils.getInstance(appContext)
                    idsToClear.forEach { notificationManager.clearNotification(it) }
                }
            } catch (e: Exception) {
                unreadCountCache = previousUnreadCount
                val bindingOrNull = _binding

                if (notificationIdsForUi.isNotEmpty()) {
                    adapter.submitList(previousList)
                    bindingOrNull?.emptyData?.visibility =
                        if (previousList.isEmpty()) View.VISIBLE else View.GONE
                }

                updateMarkAllAsReadButtonVisibility()
                updateUnreadCount()

                bindingOrNull?.let { currentBinding ->
                    Snackbar.make(
                        currentBinding.root,
                        getString(R.string.failed_to_mark_as_read),
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun getUpdatedListAfterMarkingRead(
        currentList: List<RealmNotification>,
        notificationIds: Set<String>,
        selectedFilter: String,
    ): List<RealmNotification> {
        return if (selectedFilter == "unread") {
            currentList.filterNot { notificationIds.contains(it.id) }
        } else {
            currentList.map { notification ->
                if (notificationIds.contains(notification.id) && !notification.isRead) {
                    notification.asReadCopy()
                } else {
                    notification
                }
            }.sortedWith(compareBy<RealmNotification> { it.isRead }.thenByDescending { it.createdAt })
        }
    }

    private fun RealmNotification.asReadCopy(): RealmNotification {
        return RealmNotification().also { copy ->
            copy.id = id
            copy.userId = userId
            copy.message = message
            copy.isRead = true
            copy.createdAt = Date()
            copy.type = type
            copy.relatedId = relatedId
            copy.title = title
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
