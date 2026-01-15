package org.ole.planet.myplanet.ui.teams.members

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.OnMemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.JoinedMemberData

class MembersFragment : BaseMemberFragment() {
    private var onMemberChangeListener: OnMemberChangeListener = object : OnMemberChangeListener {
        override fun onMemberChanged() {
            viewLifecycleOwner.lifecycleScope.launch {
                loadAndDisplayJoinedMembers()
            }
        }
    }
    private var adapterJoined: MembersAdapter? = null
    private var cachedJoinedMembers: List<JoinedMemberData>? = null

    fun setOnMemberChangeListener(listener: OnMemberChangeListener) {
        this.onMemberChangeListener = listener
    }

    private suspend fun loadAndDisplayJoinedMembers() {
        val joinedMembersData = teamsRepository.getJoinedMembersWithVisitInfo(teamId)
        cachedJoinedMembers = joinedMembersData
        val currentUserId = user?.id
        val isLoggedInUserLeader = joinedMembersData.any { it.user.id == currentUserId && it.isLeader }

        adapterJoined?.updateData(joinedMembersData, isLoggedInUserLeader)
        showNoData(binding.tvNodata, joinedMembersData.size, "members")
    }

    private val joinedMembers: List<JoinedMemberData>
        get() = cachedJoinedMembers ?: emptyList()

    override val list: List<RealmUserModel>
        get() = joinedMembers.map { it.user }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            loadAndDisplayJoinedMembers()
        }
    }

    override val adapter: RecyclerView.Adapter<*>
        get() {
            if (adapterJoined == null) {
                adapterJoined = MembersAdapter(
                    requireActivity(), user?.id,
                    object : MembersAdapter.MemberActionListener {
                        override fun onRemoveMember(member: JoinedMemberData, position: Int) {
                            handleRemoveMember(member)
                        }

                        override fun onMakeLeader(member: JoinedMemberData) {
                            member.user.id?.let { handleMakeLeader(it) }
                        }

                        override fun onLeaveTeam() {
                            handleLeaveTeam()
                        }
                    }
                )
            }
            return adapterJoined as MembersAdapter
        }

    private fun handleLeaveTeam() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_exit)
            .setPositiveButton(R.string.yes) { _, _ ->
                val currentUser = user
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, currentUser?.id)
                        if (nextLeader != null) {
                            nextLeader.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                        }

                        currentUser?.id?.let { userId ->
                            teamsRepository.removeMember(teamId, userId)
                        }

                        loadAndDisplayJoinedMembers()
                        onMemberChangeListener.onMemberChanged()

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

    override val layoutManager: RecyclerView.LayoutManager
        get() {
            val columns = when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
                Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
                Configuration.SCREENLAYOUT_SIZE_SMALL -> 1
                else -> 1
            }
            return GridLayoutManager(activity, columns)
        }

    private fun handleRemoveMember(member: JoinedMemberData) {
        val memberId = member.user.id ?: return
        val currentUserId = user?.id

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (currentUserId == memberId) {
                    val nextLeader = teamsRepository.getNextLeaderCandidate(teamId, currentUserId)
                    if (nextLeader != null) {
                        nextLeader.id?.let { teamsRepository.updateTeamLeader(teamId, it) }
                    } else {
                        Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                teamsRepository.removeMember(teamId, memberId)
                loadAndDisplayJoinedMembers()
                onMemberChangeListener.onMemberChanged()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMakeLeader(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                teamsRepository.updateTeamLeader(teamId, userId)
                loadAndDisplayJoinedMembers()
                Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
                onMemberChangeListener.onMemberChanged()
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
}
