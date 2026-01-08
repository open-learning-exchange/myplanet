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
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.JoinedMemberData

class MembersFragment : BaseMemberFragment() {
    private var memberChangeListener: MemberChangeListener = object : MemberChangeListener {
        override fun onMemberChanged() {
            viewLifecycleOwner.lifecycleScope.launch {
                loadAndDisplayJoinedMembers()
            }
        }
    }
    private var adapterJoined: MembersAdapter? = null
    private var cachedJoinedMembers: List<JoinedMemberData>? = null

    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.memberChangeListener = listener
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
                        val nextLeaderId = databaseService.withRealm { realm ->
                            getNextOfKinSync(realm)?.id
                        }

                        if (nextLeaderId != null) {
                            databaseService.executeTransactionAsync { realm ->
                                makeLeaderSync(realm, nextLeaderId)
                            }
                        }

                        currentUser?.id?.let { userId ->
                            teamsRepository.removeMember(teamId, userId)
                        }

                        loadAndDisplayJoinedMembers()
                        memberChangeListener.onMemberChanged()

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
                val removalResult = databaseService.withRealm { realm ->
                    if (currentUserId != memberId) {
                        RemovalResult(canRemove = true, newLeaderId = null)
                    } else {
                        val nextOfKin = getNextOfKinSync(realm)

                        val nextOfKinId = nextOfKin?.id
                        if (nextOfKinId != null) {
                            RemovalResult(canRemove = true, newLeaderId = nextOfKinId)
                        } else {
                            RemovalResult(canRemove = false, newLeaderId = null)
                        }
                    }
                }

                if (removalResult.canRemove) {
                    if (removalResult.newLeaderId != null && currentUserId == memberId) {
                        databaseService.executeTransactionAsync { realm ->
                            makeLeaderSync(realm, removalResult.newLeaderId)
                        }
                    }

                    teamsRepository.removeMember(teamId, memberId)
                    loadAndDisplayJoinedMembers()
                    memberChangeListener.onMemberChanged()
                } else {
                    Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class RemovalResult(val canRemove: Boolean, val newLeaderId: String?)

    private fun handleMakeLeader(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                databaseService.executeTransactionAsync { realm ->
                    makeLeaderSync(realm, userId)
                }
                loadAndDisplayJoinedMembers()
                Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
                memberChangeListener.onMemberChanged()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error making leader: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun makeLeaderSync(realm: Realm, userId: String) {
        val currentLeaders = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findAll()
        currentLeaders.forEach { it.isLeader = false }

        val newLeader = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", userId)
            .findFirst()

        if (newLeader != null) {
            newLeader.isLeader = true
        }
    }

    private fun getNextOfKinSync(realm: Realm): RealmUserModel? {
        val members = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", false)
            .notEqualTo("status", "archived")
            .findAll()

        if (members.isEmpty()) {
            return null
        }

        val userIds = members.mapNotNull { it.userId }.toTypedArray()
        if (userIds.isEmpty()) {
            return null
        }

        val users = realm.where(RealmUserModel::class.java)
            .`in`("id", userIds)
            .findAll()

        val userMap = users.associateBy { it.id }
        val successorMember = members.maxByOrNull { member ->
            userMap[member.userId]?.let { user ->
                val visitCount = RealmTeamLog.getVisitCount(realm, user.name, teamId)
                visitCount
            } ?: 0L
        }

        val result = successorMember?.userId?.let { id ->
            userMap[id]?.let {
                val copy = realm.copyFromRealm(it)
                copy
            }
        }
        return result
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
