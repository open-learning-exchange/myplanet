package org.ole.planet.myplanet.ui.notifications

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.R.array.status_options
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnNotificationsListener
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TasksPage

@AndroidEntryPoint
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: NotificationsAdapter
    private lateinit var userId: String
    private var notificationUpdateListener: OnNotificationsListener? = null
    private lateinit var dashboardActivity: DashboardActivity
    private var unreadCountCache: Int = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DashboardActivity) {
            dashboardActivity = context
        }
    }

    fun setNotificationUpdateListener(listener: OnNotificationsListener) {
        this.notificationUpdateListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        userId = arguments?.getString("userId") ?: ""
        adapter = NotificationsAdapter(
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
                val selectedOption = parent.getItemAtPosition(position).toString().lowercase()
                viewModel.loadNotifications(userId, selectedOption)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        viewModel.loadNotifications(userId, "all")
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collect { notifications ->
                adapter.submitList(notifications)
                binding.emptyData.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        refreshUnreadCountCache()
        binding.btnMarkAllAsRead.setOnClickListener {
            markAllAsRead()
        }
        return binding.root
    }

    private fun handleNotificationClick(notification: Notification) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = when (notification.type) {
                "survey" -> viewModel.getSurveyId(notification.relatedId)
                "task" -> viewModel.getTaskDetails(notification.relatedId)
                "join_request" -> notification.relatedId?.let {
                    viewModel.getJoinRequestTeamId(it)
                }
                else -> null
            }

            when (notification.type) {
                "storage" -> {
                    val intent = Intent(ACTION_INTERNAL_STORAGE_SETTINGS)
                    startActivity(intent)
                }
                "survey" -> {
                    val examId = result as? String
                    if (examId != null && activity is OnHomeItemClickListener) {
                        SubmissionsAdapter.openSurvey(
                            activity as OnHomeItemClickListener,
                            examId,
                            false,
                            false,
                            "",
                        )
                    }
                }
                "task" -> {
                    val teamDetails = result as? Triple<String, String?, String?>
                    if (teamDetails != null && activity is OnHomeItemClickListener) {
                        val (teamId, teamName, teamType) = teamDetails
                        val f = TeamDetailFragment.newInstance(
                            teamId = teamId,
                            teamName = teamName ?: "",
                            teamType = teamType ?: "",
                            isMyTeam = true,
                            navigateToPage = TasksPage,
                        )
                        (activity as OnHomeItemClickListener).openCallFragment(f)
                    }
                }
                "join_request" -> {
                    val teamId = result as? String
                    if (teamId?.isNotEmpty() == true && activity is OnHomeItemClickListener) {
                        val f = TeamDetailFragment()
                        val b = Bundle()
                        b.putString("id", teamId)
                        b.putBoolean("isMyTeam", true)
                        b.putString("navigateToPage", JoinRequestsPage.id)
                        f.arguments = b
                        (activity as OnHomeItemClickListener).openCallFragment(f)
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

    private fun markAsReadById(notificationId: String) {
        viewModel.markAsRead(notificationId, userId)
    }

    private fun markAllAsRead() {
        viewModel.markAllAsRead(userId)
    }

    private fun updateMarkAllAsReadButtonVisibility() {
        _binding?.let { binding ->
            binding.btnMarkAllAsRead.visibility = if (unreadCountCache > 0) View.VISIBLE else View.GONE
        }
    }

    private fun updateUnreadCount() {
        notificationUpdateListener?.onNotificationCountUpdated(unreadCountCache)
    }

    fun refreshNotificationsList() {
        if (::adapter.isInitialized && _binding != null) {
            val selectedFilter = binding.status.selectedItem.toString().lowercase()
            viewModel.loadNotifications(userId, selectedFilter)
            refreshUnreadCountCache()
        }
    }

    private fun refreshUnreadCountCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.getUnreadCount(userId)
            unreadCountCache = count
            updateMarkAllAsReadButtonVisibility()
            updateUnreadCount()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
