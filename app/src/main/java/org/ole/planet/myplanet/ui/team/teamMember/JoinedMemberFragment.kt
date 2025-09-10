package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
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
                val offlineVisits = profileDbHandler.getOfflineVisits(member).toString()
                val profileLastVisit = profileDbHandler.getLastVisit(member)
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
            val canRemove = databaseService.withRealmAsync { realm ->
                val currentUserId = user?.id
                if (currentUserId != member.user.id) {
                    member.user.id?.let { removeMember(realm, it) }
                    true
                } else {
                    val nextOfKin = getNextOfKin(realm)
                    if (nextOfKin != null && nextOfKin.id != null) {
                        makeLeader(realm, nextOfKin.id!!)
                        member.user.id?.let { removeMember(realm, it) }
                        true
                    } else {
                        false
                    }
                }
            }
            if (canRemove) {
                memberChangeListener.onMemberChanged()
                refreshMembers()
            } else {
                Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMakeLeader(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            databaseService.withRealmAsync { realm ->
                makeLeader(realm, userId)
            }
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

    private fun makeLeader(realm: Realm, userId: String) {
        realm.executeTransaction { r ->
            val currentLeader = r.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()
            val newLeader = r.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst()
            currentLeader?.isLeader = false
            newLeader?.isLeader = true
        }
    }

    private fun removeMember(realm: Realm, userId: String) {
        realm.executeTransaction { r ->
            val team = r.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst()
            team?.deleteFromRealm()
        }
    }

    private fun getNextOfKin(realm: Realm): RealmUserModel? {
        val members: List<RealmMyTeam> = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", false)
            .notEqualTo("status", "archived")
            .findAll()

        if (members.isEmpty()) {
            return null
        }

        var successorTeamMember: RealmMyTeam? = null
        var maxVisitCount: Long = -1

        for (member in members) {
            val user = realm.where(RealmUserModel::class.java).equalTo("id", member.userId).findFirst()
            if (user != null) {
                val visitCount = RealmTeamLog.getVisitCount(realm, user.name, teamId)
                if (visitCount > maxVisitCount) {
                    maxVisitCount = visitCount
                    successorTeamMember = member
                }
            }
        }

        return successorTeamMember?.userId?.let { id ->
            realm.where(RealmUserModel::class.java).equalTo("id", id).findFirst()?.let { realm.copyFromRealm(it) }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}

