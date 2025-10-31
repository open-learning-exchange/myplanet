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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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
    private val viewModel: NotificationsViewModel by viewModels()
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
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        userId = arguments?.getString("userId") ?: ""

        setupAdapter()
        setupSpinner()
        setupObservers()

        binding.btnMarkAllAsRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        viewModel.initialize(userId)

        return binding.root
    }

    private fun setupAdapter() {
        adapter = AdapterNotification(
            onMarkAsReadClick = { notificationId ->
                viewModel.markNotificationAsRead(notificationId)
            },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSpinner() {
        val options = resources.getStringArray(status_options)
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, options.toMutableList())
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.status.adapter = spinnerAdapter
        binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString()
                viewModel.updateFilter(NotificationFilter.fromValue(selectedOption))
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.notificationItems.collectLatest { notificationItems ->
                adapter.submitList(notificationItems)
                binding.emptyData.visibility = if (notificationItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.unreadCount.collectLatest { unreadCount ->
                binding.btnMarkAllAsRead.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                notificationUpdateListener?.onNotificationCountUpdated(unreadCount)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is NotificationEvent.ShowMessage -> {
                        Snackbar.make(binding.root, getString(event.messageRes), Snackbar.LENGTH_LONG).show()
                    }
                    is NotificationEvent.ClearNotifications -> {
                        val appContext = requireContext().applicationContext
                        withContext(Dispatchers.IO) {
                            val notificationManager = NotificationUtils.getInstance(appContext)
                            event.notificationIds.forEach { notificationManager.clearNotification(it) }
                        }
                    }
                }
            }
        }
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

        viewModel.markNotificationAsRead(notification.id)
    }

    fun refreshNotificationsList() {
        viewModel.refresh()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
