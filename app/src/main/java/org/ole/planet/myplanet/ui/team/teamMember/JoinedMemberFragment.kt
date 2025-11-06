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

    private var _joinedMembers: List<JoinedMemberData>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            loadJoinedMembers()
            if (isAdded) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun loadJoinedMembers() {
        val membersData = withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
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
        }

        _joinedMembers = membersData
        if (isAdded) {
            setupAdapter()
        }
    }

    private fun setupAdapter() {
        val members = _joinedMembers ?: return
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
        binding.rvMember.adapter = adapterJoined
        showNoData(binding.tvNodata, adapterJoined?.itemCount ?: 0, "members")
    }


    override val list: List<RealmUserModel>
        get() = _joinedMembers?.map { it.user } ?: emptyList()

    override val adapter: RecyclerView.Adapter<*>
        get() {
            if (adapterJoined == null) {
                setupAdapter()
            }
            return adapterJoined ?: AdapterJoinedMember(requireActivity(), mutableListOf(), false, object : AdapterJoinedMember.MemberActionListener {
                override fun onRemoveMember(member: JoinedMemberData, position: Int) {}
                override fun onMakeLeader(member: JoinedMemberData) {}
            })
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

                    teamRepository.removeMember(teamId, memberId)

                    adapterJoined?.removeMember(memberId)

                    removalResult.newLeaderId?.let { newLeaderId ->
                        adapterJoined?.updateLeadership(currentUserId, newLeaderId)
                    }

                    memberChangeListener.onMemberChanged()
                    showNoData(binding.tvNodata, adapterJoined?.itemCount ?: 0, "members")
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

                val currentUserId = user?.id
                adapterJoined?.updateLeadership(currentUserId, userId)
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

