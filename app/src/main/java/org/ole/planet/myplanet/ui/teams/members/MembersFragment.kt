package org.ole.planet.myplanet.ui.teams.members

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.callback.OnMemberActionListener
import org.ole.planet.myplanet.databinding.FragmentCombinedMembersBinding
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.DialogUtils.confirmDialog
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class MembersFragment : BaseTeamFragment() {

    private val requestsViewModel: RequestsViewModel by viewModels()
    private var _binding: FragmentCombinedMembersBinding? = null
    private val binding get() = _binding!!

    private var onMemberChangeListener: OnChangedListener? = null
    private var membersAdapter: MembersAdapter? = null
    private var requestsAdapter: RequestsAdapter? = null

    fun setOnMemberChangeListener(listener: OnChangedListener) {
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

        val initialUser = UserEntity()
        requestsAdapter = RequestsAdapter(requireActivity(), initialUser) { reqUser, isAccepted ->
            requestsViewModel.respondToRequest(teamId, reqUser, isAccepted)
        }.apply { setTeamId(teamId) }
        binding.rvRequests.layoutManager = GridLayoutManager(activity, columns)
        binding.rvRequests.adapter = requestsAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            val resolvedUser = ensureUserResolved() ?: UserEntity()
            requestsAdapter?.setUser(resolvedUser)
            membersAdapter?.setUserId(resolvedUser.id)
        }

        loadMembers()

        requestsViewModel.fetchMembers(teamId)

        collectWhenStarted(requestsViewModel.uiState) { state ->
            requestsAdapter?.setData(state.members, state.isLeader, state.memberCount)
            val hasRequests = state.members.isNotEmpty()
            binding.llRequestsSection.visibility = if (hasRequests) View.VISIBLE else View.GONE
            if (hasRequests) {
                binding.tvRequestsHeader.text = getString(R.string.join_requests) + " (${state.members.size})"
            }
        }
        collectWhenStarted(requestsViewModel.successAction) {
            onMemberChangeListener?.onChanged()
            loadMembers()
        }
        collectWhenStarted(requestsViewModel.membersState) { state ->
            membersAdapter?.setUserId(state.currentUserId)
            membersAdapter?.updateData(state.members, state.isLeader)
            BaseRecyclerFragment.showNoData(binding.tvNodata, state.members.size, "")
        }
        collectWhenStarted(requestsViewModel.actionResults) { result ->
            when (result) {
                MemberActionResult.LeftTeam -> {
                    Toast.makeText(requireContext(), getString(R.string.left_team), Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                }
                MemberActionResult.MemberRemoved -> {
                    onMemberChangeListener?.onChanged()
                    requestsViewModel.fetchMembers(teamId)
                }
                MemberActionResult.CannotRemoveLastLeader -> {
                    Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                }
                MemberActionResult.LeaderChanged -> {
                    Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
                    onMemberChangeListener?.onChanged()
                }
                is MemberActionResult.Failed -> {
                    val actionStr = when (result.action) {
                        MemberAction.LEAVE_TEAM -> "leaving team"
                        MemberAction.REMOVE_MEMBER -> "removing member"
                        MemberAction.MAKE_LEADER -> "making leader"
                    }
                    Toast.makeText(requireContext(), "Error $actionStr: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMembers() {
        requestsViewModel.loadJoinedMembers(teamId)
    }

    private fun handleLeaveTeam() {
        requireContext().confirmDialog(
            message = getString(R.string.confirm_exit),
            onPositive = {
                requestsViewModel.leaveTeam(teamId)
            }
        )
    }

    private fun handleRemoveMember(member: JoinedMemberData) {
        val memberId = member.user.id ?: return
        requestsViewModel.removeMember(teamId, memberId)
    }

    private fun handleMakeLeader(userId: String) {
        requestsViewModel.makeLeader(teamId, userId)
    }

    override fun onNewsItemClick(news: News?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
