package org.ole.planet.myplanet.ui.notifications

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
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.R.array.status_options
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnNotificationsListener
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.teams.TeamPageConfig
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ChatPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.ui.voices.ReplyActivity
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: NotificationsAdapter
    private lateinit var userId: String
    private var notificationUpdateListener: OnNotificationsListener? = null
    private var currentFilter: String = "all"
    private var isAdmin: Boolean = false

    fun setNotificationUpdateListener(listener: OnNotificationsListener) {
        this.notificationUpdateListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        userId = arguments?.getString("userId") ?: ""
        isAdmin = arguments?.getBoolean("isAdmin", false) ?: false

        adapter = NotificationsAdapter(
            onMarkAsReadClick = { notificationId -> viewModel.markAsRead(notificationId) },
            onNotificationClick = { notification -> handleNotificationClick(notification) },
            onToggleSelection = { notificationId -> viewModel.toggleSelection(notificationId) },
            onToggleGroupExpansion = { type -> viewModel.toggleGroupExpansion(type) }
        )
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())

        val options = resources.getStringArray(status_options)
        val optionsList: MutableList<String?> = ArrayList(listOf(*options))
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_right, optionsList)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.status.adapter = spinnerAdapter
        var isInitialSpinnerSelection = true
        binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isInitialSpinnerSelection) {
                    isInitialSpinnerSelection = false
                    return
                }
                currentFilter = parent.getItemAtPosition(position).toString().lowercase(Locale.ROOT)
                viewModel.loadNotifications(userId, currentFilter, isAdmin)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnMarkAllAsRead.setOnClickListener { viewModel.markAllAsRead(userId) }
        binding.btnBulkMarkAsRead.setOnClickListener { viewModel.markSelectedAsRead() }
        binding.btnBulkDelete.setOnClickListener { viewModel.deleteSelected() }
        binding.btnCancelSelection.setOnClickListener { viewModel.clearSelection() }

        viewModel.loadNotifications(userId, "all", isAdmin)

        collectWhenStarted(viewModel.groupedItems) { items ->
            adapter.submitList(items)
            val isEmpty = items.isEmpty()
            binding.emptyData.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.emptyData.text = when (currentFilter) {
                "unread" -> getString(R.string.no_unread_notifications)
                "read" -> getString(R.string.no_read_notifications)
                else -> getString(R.string.no_notifications)
            }
            binding.status.visibility = if (isEmpty && currentFilter == "all") View.GONE else View.VISIBLE
        }
        collectWhenStarted(viewModel.unreadCount) { count ->
            notificationUpdateListener?.onNotificationCountUpdated(count)
            val showButton = count > 0 && currentFilter != "read"
            binding.btnMarkAllAsRead.visibility = if (showButton) View.VISIBLE else View.GONE
        }
        collectWhenStarted(viewModel.isSelectionMode) { inSelectionMode ->
            binding.ltBulkActionBar.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
            binding.ltTopBar.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        }
        collectWhenStarted(viewModel.selectedCount) { count ->
            binding.tvSelectedCount.text = getString(R.string.selected_count, count)
        }

        return binding.root
    }

    private fun handleNotificationClick(notification: Notification) {
        when (notification.type) {
            "join_request" -> resolveAndOpenTeam(notification.relatedId, JoinRequestsPage) { relatedId ->
                viewModel.getJoinRequestTeamId(relatedId)
            }
            "team_join" -> openTeam(notification.relatedId, navigateToPage = null)
            "chat" -> openTeam(notification.relatedId, ChatPage)
            "task" -> resolveAndOpenTeam(notification.relatedId, TasksPage) { relatedId ->
                viewModel.getTaskDetails(relatedId)?.teamId
            }
            "voice_reply" -> notification.relatedId?.let { newsId ->
                startActivity(Intent(requireContext(), ReplyActivity::class.java).putExtra("id", newsId))
            }
            "resource" -> (activity as? OnHomeItemClickListener)?.openMyFragment(ResourcesFragment())
            "storage" -> startActivity(Intent(ACTION_INTERNAL_STORAGE_SETTINGS))
        }

        if (!notification.isRead) {
            viewModel.markAsRead(notification.id)
        }
    }

    /**
     * [relatedId] is either a task/join-request id (resolved to a team id via [resolve]) or
     * already a team id (server-synced notifications carry the team id directly). When [resolve]
     * can't match it to a known task/join-request, [relatedId] is used as-is.
     */
    private fun resolveAndOpenTeam(relatedId: String?, navigateToPage: TeamPageConfig?, resolve: suspend (String) -> String?) {
        if (relatedId.isNullOrEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val teamId = resolve(relatedId) ?: relatedId
            openTeam(teamId, navigateToPage)
        }
    }

    private fun openTeam(teamId: String?, navigateToPage: TeamPageConfig?) {
        if (teamId.isNullOrEmpty()) return
        val listener = activity as? OnHomeItemClickListener ?: return
        listener.openCallFragment(
            TeamDetailFragment.newInstance(
                teamId = teamId,
                teamName = "",
                teamType = "",
                isMyTeam = true,
                navigateToPage = navigateToPage,
            )
        )
    }

    fun refreshNotificationsList() {
        if (::adapter.isInitialized && _binding != null) {
            currentFilter = binding.status.selectedItem.toString().lowercase(Locale.ROOT)
            viewModel.loadNotifications(userId, currentFilter, isAdmin)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
