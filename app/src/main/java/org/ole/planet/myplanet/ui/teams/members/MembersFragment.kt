package org.ole.planet.myplanet.ui.teams.members

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.callback.OnMemberActionListener
import org.ole.planet.myplanet.callback.OnMemberChangeListener
import org.ole.planet.myplanet.databinding.FragmentCombinedMembersBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.JoinedMemberData
import org.ole.planet.myplanet.services.UserSessionManager

@AndroidEntryPoint
class MembersFragment : BaseTeamFragment() {

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val requestsViewModel: RequestsViewModel by viewModels()
    private var _binding: FragmentCombinedMembersBinding? = null
    private val binding get() = _binding!!

    private var onMemberChangeListener: OnMemberChangeListener? = null
    private var membersAdapter: MembersAdapter? = null
    private var requestsAdapter: RequestsAdapter? = null

    fun setOnMemberChangeListener(listener: OnMemberChangeListener) {
        onMemberChangeListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCombinedMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvMembersHeader.text = getString(R.string.members)

        val columns = when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
            Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
            else -> 1
        }

        membersAdapter = MembersAdapter(requireActivity(), user?.id, object : OnMemberActionListener {
            override fun onRemoveMember(member: JoinedMemberData, position: Int) = handleRemoveMember(member)
            override fun onMakeLeader(member: JoinedMemberData) { member.user.id?.let { handleMakeLeader(it) } }
            override fun onLeaveTeam() = handleLeaveTeam()
        })
        binding.rvMembers.layoutManager = GridLayoutManager(activity, columns)
        binding.rvMembers.adapter = membersAdapter

        val initialUser = RealmUser()
        requestsAdapter = RequestsAdapter(requireActivity(), initialUser) { reqUser, isAccepted ->
            requestsViewModel.respondToRequest(teamId, reqUser, isAccepted)
        }.apply { setTeamId(teamId) }
        binding.rvRequests.layoutManager = GridLayoutManager(activity, columns)
        binding.rvRequests.adapter = requestsAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            val resolvedUser = userSessionManager.getUserModel() ?: RealmUser()
            requestsAdapter?.setUser(resolvedUser)
            membersAdapter?.setUserId(resolvedUser.id)
        }

        loadMembers()

        requestsViewModel.fetchMembers(teamId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    requestsViewModel.uiState.collect { state ->
                        requestsAdapter?.setData(state.members, state.isLeader, state.memberCount)
                        val hasRequests = state.members.isNotEmpty()
                        binding.llRequestsSection.visibility = if (hasRequests) View.VISIBLE else View.GONE
                        if (hasRequests) {
                            binding.tvRequestsHeader.text = getString(R.string.join_requests) + " (${state.members.size})"
                        }
                    }
                }
                launch {
                    requestsViewModel.successAction.collect {
                        onMemberChangeListener?.onMemberChanged()
                        loadMembers()
                    }
                }
            }
        }
    }

    private fun loadMembers() {
        viewLifecycleOwner.lifecycleScope.launch {
            val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)
            val currentUserId = userSessionManager.getUserModel()?.id
            val isLeader = members.any { it.user.id == currentUserId && it.isLeader }
            membersAdapter?.setUserId(currentUserId)
            membersAdapter?.updateData(members, isLeader)
            if (members.isEmpty()) {
                binding.tvNodata.visibility = View.VISIBLE
                binding.tvNodata.text = getString(R.string.no_data_available_please_check_and_try_again)
            } else {
                binding.tvNodata.visibility = View.GONE
            }
        }
    }

    private fun handleLeaveTeam() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_exit)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, user?.id)
                        nextLeader?.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                        user?.id?.let { teamsRepository.removeMember(teamId, it) }
                        loadMembers()
                        onMemberChangeListener?.onMemberChanged()
                        Toast.makeText(requireContext(), getString(R.string.left_team), Toast.LENGTH_SHORT).show()
                        requireActivity().supportFragmentManager.popBackStack()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error leaving team: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun handleRemoveMember(member: JoinedMemberData) {
        val memberId = member.user.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (user?.id == memberId) {
                    val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, memberId)
                    if (nextLeader != null) {
                        nextLeader.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                    } else {
                        Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }
                teamsRepository.removeMember(teamId, memberId)
                loadMembers()
                onMemberChangeListener?.onMemberChanged()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMakeLeader(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                teamsRepository.updateTeamLeader(teamId, userId)
                loadMembers()
                Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
                onMemberChangeListener?.onMemberChanged()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error making leader: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
