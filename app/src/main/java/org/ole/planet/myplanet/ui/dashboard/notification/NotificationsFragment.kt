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
import org.ole.planet.myplanet.repository.NotificationFilter
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

        adapter = AdapterNotification(
            databaseService,
            emptyList(),
            onMarkAsReadClick = { notificationId ->
                markAsReadById(notificationId)
            },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        val options = resources.getStringArray(status_options)
        val optionsList: MutableList<String?> = ArrayList(listOf(*options))
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, optionsList)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.status.adapter = spinnerAdapter
        binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString()
                loadNotifications(NotificationFilter.fromSelection(selectedOption))
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnMarkAllAsRead.setOnClickListener {
            markAllAsRead()
        }
        updateMarkAllAsReadButtonVisibility()
        updateUnreadCount()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNotifications(getSelectedFilter())
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

    private fun loadNotifications(filter: NotificationFilter) {
        viewLifecycleOwner.lifecycleScope.launch {
            updateNotificationsForFilter(filter)
        }
    }

    private suspend fun updateNotificationsForFilter(filter: NotificationFilter) {
        val notifications = notificationRepository.getNotificationsForUser(userId, filter)
        unreadCountCache = notificationRepository.getUnreadCount(userId.takeIf { it.isNotEmpty() })
        adapter.updateNotifications(notifications)
        binding.emptyData.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        updateMarkAllAsReadButtonVisibility()
        updateUnreadCount()
    }

    private fun getSelectedFilter(): NotificationFilter {
        val selection = binding.status.selectedItem?.toString().orEmpty()
        return NotificationFilter.fromSelection(selection)
    }

    private fun markAsReadById(notificationId: String) {
        markNotificationsAsRead {
            notificationRepository.markNotificationsAsRead(setOf(notificationId))
        }
    }

    private fun markAllAsRead() {
        markNotificationsAsRead {
            notificationRepository.markAllUnreadAsRead(userId.takeIf { it.isNotEmpty() })
        }
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        binding.btnMarkAllAsRead.visibility = if (unreadCountCache > 0) View.VISIBLE else View.GONE
    }

    private fun updateUnreadCount() {
        notificationUpdateListener?.onNotificationCountUpdated(unreadCountCache)
    }

    fun refreshNotificationsList() {
        if (::adapter.isInitialized && _binding != null) {
            loadNotifications(getSelectedFilter())
        }
    }

    private fun markNotificationsAsRead(
        backgroundAction: suspend () -> Set<String>,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val selectedFilter = getSelectedFilter()
            val appContext = requireContext().applicationContext

            try {
                val idsToClear = withContext(Dispatchers.IO) { backgroundAction() }
                updateNotificationsForFilter(selectedFilter)
                withContext(Dispatchers.IO) {
                    val notificationManager = NotificationUtils.getInstance(appContext)
                    idsToClear.forEach { notificationManager.clearNotification(it) }
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.failed_to_mark_as_read), Snackbar.LENGTH_LONG).show()
                loadNotifications(selectedFilter)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
