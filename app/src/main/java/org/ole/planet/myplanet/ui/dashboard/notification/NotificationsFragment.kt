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
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.R.array.status_options
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.utilities.NotificationUtils

@AndroidEntryPoint
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
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
        val binding = _binding!!
        userId = arguments?.getString("userId") ?: ""

        val options = resources.getStringArray(status_options)
        val optionsList: MutableList<String?> = ArrayList(listOf(*options))
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, optionsList)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.status.adapter = spinnerAdapter
        binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                loadNotificationsForFilter(selectedOption)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        adapter = AdapterNotification(
            notificationRepository,
            viewLifecycleOwner.lifecycleScope,
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
        loadNotificationsForFilter("all")
        return binding.root
    }

    private fun handleNotificationClick(notification: RealmNotification) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (notification.type) {
                "storage" -> {
                    val intent = Intent(ACTION_INTERNAL_STORAGE_SETTINGS)
                    startActivity(intent)
                }
                "survey" -> {
                    val surveyTarget = withContext(Dispatchers.IO) {
                        notificationRepository.resolveSurveyStepId(notification.relatedId)
                    }
                    val onHomeListener = activity as? OnHomeItemClickListener
                    if (surveyTarget != null && onHomeListener != null) {
                        AdapterMySubmission.openSurvey(
                            onHomeListener,
                            surveyTarget.stepId,
                            false,
                            false,
                            "",
                        )
                    }
                }
                "task" -> {
                    val taskNavigation = withContext(Dispatchers.IO) {
                        notificationRepository.resolveTaskNavigation(notification.relatedId)
                    }
                    val onHomeListener = activity as? OnHomeItemClickListener
                    if (taskNavigation != null && onHomeListener != null) {
                        val fragment = TeamDetailFragment.newInstance(
                            teamId = taskNavigation.teamId,
                            teamName = taskNavigation.teamName ?: "",
                            teamType = taskNavigation.teamType ?: "",
                            isMyTeam = true,
                            navigateToPage = TasksPage,
                        )
                        onHomeListener.openCallFragment(fragment)
                    }
                }
                "join_request" -> {
                    val joinRequestNavigation = withContext(Dispatchers.IO) {
                        notificationRepository.resolveJoinRequestTeam(notification.relatedId)
                    }
                    val onHomeListener = activity as? OnHomeItemClickListener
                    if (joinRequestNavigation != null && onHomeListener != null) {
                        val targetPage = if (joinRequestNavigation.teamType?.equals("enterprise", ignoreCase = true) == true) {
                            ApplicantsPage
                        } else {
                            JoinRequestsPage
                        }
                        val fragment = TeamDetailFragment()
                        fragment.arguments = Bundle().apply {
                            putString("id", joinRequestNavigation.teamId)
                            putString("teamId", joinRequestNavigation.teamId)
                            joinRequestNavigation.teamType?.let { putString("teamType", it) }
                            putBoolean("isMyTeam", true)
                            putString("navigateToPage", targetPage.id)
                        }
                        onHomeListener.openCallFragment(fragment)
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
    }

    private fun loadNotificationsForFilter(filter: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch
            val (notifications, unreadCount) = fetchNotificationsAndUnreadCount(filter)
            val binding = _binding ?: return@launch
            adapter.submitList(notifications)
            binding.emptyData.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            unreadCountCache = unreadCount
            updateMarkAllAsReadButtonVisibility()
            updateUnreadCount()
        }
    }

    private suspend fun fetchNotificationsAndUnreadCount(filter: String): Pair<List<RealmNotification>, Int> =
        withContext(Dispatchers.IO) {
            val notifications = notificationRepository.getNotifications(userId, filter)
            val unreadCount = notificationRepository.getUnreadCount(userId)
            notifications to unreadCount
        }

    private fun markAsReadById(notificationId: String) {
        markNotificationsAsRead(setOf(notificationId), isMarkAll = false) {
            notificationRepository.markAsRead(notificationId)
            setOf(notificationId)
        }
    }

    private fun markAllAsRead() {
        val notificationIds = adapter.currentList.map { it.id }.toSet()
        markNotificationsAsRead(notificationIds, isMarkAll = true) {
            notificationRepository.markAllAsRead(userId)
            notificationRepository.getNotifications(userId, "all").map { it.id }.toSet()
        }
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        _binding?.btnMarkAllAsRead?.visibility = if (unreadCountCache > 0) View.VISIBLE else View.GONE
    }

    private fun updateUnreadCount() {
        notificationUpdateListener?.onNotificationCountUpdated(unreadCountCache)
    }

    fun refreshNotificationsList() {
        if (::adapter.isInitialized) {
            val binding = _binding ?: return
            val selectedFilter = binding.status.selectedItem.toString().lowercase()
            loadNotificationsForFilter(selectedFilter)
        }
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
            val appContext = context?.applicationContext ?: return@launch

            val updatedList = if (notificationIdsForUi.isNotEmpty()) {
                getUpdatedListAfterMarkingRead(previousList, notificationIdsForUi, selectedFilter)
            } else {
                previousList
            }

            if (notificationIdsForUi.isNotEmpty()) {
                adapter.submitList(updatedList)
                _binding?.emptyData?.visibility = if (updatedList.isEmpty()) View.VISIBLE else View.GONE
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
                if (_binding == null) {
                    return@launch
                }
            } catch (e: Exception) {
                unreadCountCache = previousUnreadCount
                if (notificationIdsForUi.isNotEmpty()) {
                    adapter.submitList(previousList)
                    _binding?.emptyData?.visibility = if (previousList.isEmpty()) View.VISIBLE else View.GONE
                }
                updateMarkAllAsReadButtonVisibility()
                updateUnreadCount()
                _binding?.let { currentBinding ->
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
            }
        }
    }

    private fun RealmNotification.asReadCopy(): RealmNotification {
        return RealmNotification().also { copy ->
            copy.id = id
            copy.userId = userId
            copy.message = message
            copy.isRead = true
            copy.createdAt = createdAt
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
