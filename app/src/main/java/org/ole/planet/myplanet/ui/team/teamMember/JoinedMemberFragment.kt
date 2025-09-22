package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel

class JoinedMemberFragment : BaseMemberFragment() {
    private var memberChangeListener: MemberChangeListener = object : MemberChangeListener {
        override fun onMemberChanged() {
            // fallback listener to prevent crash
        }
    }

    private var adapterJoined: AdapterJoinedMember? = null

    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.memberChangeListener = listener
    }

    private val joinedMembers: List<JoinedMemberData>
        get() = databaseService.withRealm { realm ->
            val members = getJoinedMember(teamId, realm).map { realm.copyFromRealm(it) }.toMutableList()
            val leaderId = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()?.userId
            val leader = members.find { it.id == leaderId }
            if (leader != null) {
                members.remove(leader)
                members.add(0, leader)
            }
            members.map { member ->
                val lastVisitTimestamp = RealmTeamLog.getLastVisit(realm, member.name, teamId)
                val lastVisitDate = if (lastVisitTimestamp != null) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    sdf.format(Date(lastVisitTimestamp))
                } else {
                    getString(R.string.no_visit)
                }
                val visitCount = RealmTeamLog.getVisitCount(realm, member.name, teamId)
                val offlineVisits = profileDbHandler?.getOfflineVisits(member)?.toString() ?: "0"
                val profileLastVisit = profileDbHandler?.getLastVisit(member) ?: ""
                JoinedMemberData(
                    member,
                    visitCount,
                    lastVisitDate,
                    offlineVisits,
                    profileLastVisit,
                    member.id == leaderId
                )
            }
        }

    override val list: List<RealmUserModel>
        get() = joinedMembers.map { it.user }

    override val adapter: RecyclerView.Adapter<*>
        get() {
            if (adapterJoined == null) {
                val members = joinedMembers
                val currentUserId = user?.id
                val isLeader = members.any { it.user.id == currentUserId && it.isLeader }
                adapterJoined = AdapterJoinedMember(
                    requireActivity(),
                    members.toMutableList(),
                    isLeader,
                    object : AdapterJoinedMember.MemberActionListener {
                        override fun onRemoveMember(member: JoinedMemberData, position: Int) {
                            handleRemoveMember(member)
                        }

                        override fun onMakeLeader(member: JoinedMemberData) {
                            member.user.id?.let { handleMakeLeader(it) }
                        }
                    }
                )
            }
            return adapterJoined as AdapterJoinedMember
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
        viewLifecycleOwner.lifecycleScope.launch {
            val memberId = member.user.id
            val currentUserId = user?.id
            if (memberId.isNullOrBlank()) return@launch

            val canRemove = if (currentUserId != memberId) {
                true
            } else {
                val nextOfKinId = getNextOfKin(memberId)?.id
                if (nextOfKinId.isNullOrBlank()) {
                    false
                } else {
                    teamRepository.promoteMemberToLeader(teamId, nextOfKinId)
                    true
                }
            }

            if (canRemove) {
                teamRepository.removeMember(teamId, memberId)
                memberChangeListener.onMemberChanged()
                refreshMembers()
            } else {
                Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMakeLeader(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            teamRepository.promoteMemberToLeader(teamId, userId)
            Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
            memberChangeListener.onMemberChanged()
            refreshMembers()
        }
    }

    private fun refreshMembers() {
        val members = joinedMembers
        val currentUserId = user?.id
        val isLeader = members.any { it.user.id == currentUserId && it.isLeader }
        adapterJoined?.updateData(members.toMutableList(), isLeader)
        showNoData(binding.tvNodata, members.size, "members")
    }

    private fun getNextOfKin(excludeUserId: String?): RealmUserModel? {
        if (excludeUserId.isNullOrBlank()) return null
        return joinedMembers
            .asSequence()
            .filter { !it.isLeader }
            .filter { it.user.id != excludeUserId }
            .filter { !it.user.isArchived }
            .maxByOrNull { it.visitCount }
            ?.user
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}

