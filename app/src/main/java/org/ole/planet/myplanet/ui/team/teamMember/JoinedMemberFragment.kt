package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import android.util.Log
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
    companion object {
        private const val TAG = "JoinedMemberFragment"
    }

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
        val memberId = member.user.id ?: return
        val currentUserId = user?.id
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "handleRemoveMember: Starting for memberId=$memberId, currentUserId=$currentUserId at $startTime")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dbStartTime = System.currentTimeMillis()
                Log.d(TAG, "handleRemoveMember: Starting database operation")

                val removalResult = databaseService.withRealm { realm ->
                    Log.d(TAG, "handleRemoveMember: Inside realm query for next of kin")
                    if (currentUserId != memberId) {
                        Log.d(TAG, "handleRemoveMember: Non-leader removal")
                        RemovalResult(canRemove = true, newLeaderId = null)
                    } else {
                        Log.d(TAG, "handleRemoveMember: Leader removal, finding next of kin")
                        val kinStartTime = System.currentTimeMillis()
                        val nextOfKin = getNextOfKinSync(realm)
                        Log.d(TAG, "handleRemoveMember: getNextOfKin took ${System.currentTimeMillis() - kinStartTime}ms")

                        val nextOfKinId = nextOfKin?.id
                        if (nextOfKinId != null) {
                            Log.d(TAG, "handleRemoveMember: Found next of kin: $nextOfKinId")
                            RemovalResult(canRemove = true, newLeaderId = nextOfKinId)
                        } else {
                            Log.d(TAG, "handleRemoveMember: No next of kin found, cannot remove")
                            RemovalResult(canRemove = false, newLeaderId = null)
                        }
                    }
                }

                if (removalResult.canRemove) {
                    Log.d(TAG, "handleRemoveMember: Starting database transaction")
                    val transactionStartTime = System.currentTimeMillis()

                    // Use the same approach as handleMakeLeader for consistency
                    if (removalResult.newLeaderId != null && currentUserId == memberId) {
                        Log.d(TAG, "handleRemoveMember: Making new leader first")
                        val leaderStartTime = System.currentTimeMillis()
                        databaseService.executeTransactionAsync { realm ->
                            makeLeaderSync(realm, removalResult.newLeaderId)
                        }
                        Log.d(TAG, "handleRemoveMember: Leader transaction completed in ${System.currentTimeMillis() - leaderStartTime}ms")
                    }

                    Log.d(TAG, "handleRemoveMember: Removing member")
                    val removeStartTime = System.currentTimeMillis()
                    databaseService.executeTransactionAsync { realm ->
                        removeMemberSync(realm, memberId)
                    }
                    Log.d(TAG, "handleRemoveMember: Remove transaction completed in ${System.currentTimeMillis() - removeStartTime}ms")

                    Log.d(TAG, "handleRemoveMember: Database transaction completed in ${System.currentTimeMillis() - transactionStartTime}ms")
                    val dbEndTime = System.currentTimeMillis()
                    Log.d(TAG, "handleRemoveMember: Database operation completed in ${dbEndTime - dbStartTime}ms")

                    val adapterStartTime = System.currentTimeMillis()
                    Log.d(TAG, "handleRemoveMember: Starting adapter updates")

                    adapterJoined?.removeMember(memberId)
                    Log.d(TAG, "handleRemoveMember: Member removed from adapter")

                    removalResult.newLeaderId?.let { newLeaderId ->
                        Log.d(TAG, "handleRemoveMember: Updating leadership in adapter")
                        adapterJoined?.updateLeadership(currentUserId, newLeaderId)
                    }

                    val adapterEndTime = System.currentTimeMillis()
                    Log.d(TAG, "handleRemoveMember: Adapter updates completed in ${adapterEndTime - adapterStartTime}ms")

                    val listenerStartTime = System.currentTimeMillis()
                    memberChangeListener.onMemberChanged()
                    Log.d(TAG, "handleRemoveMember: Member change listener called in ${System.currentTimeMillis() - listenerStartTime}ms")

                    val uiStartTime = System.currentTimeMillis()
                    showNoData(binding.tvNodata, adapterJoined?.itemCount ?: 0, "members")
                    Log.d(TAG, "handleRemoveMember: UI update completed in ${System.currentTimeMillis() - uiStartTime}ms")
                } else {
                    Log.d(TAG, "handleRemoveMember: Cannot remove user, showing toast")
                    Toast.makeText(requireContext(), R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                }

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "handleRemoveMember: Total operation completed in ${totalTime}ms")

            } catch (e: Exception) {
                Log.e(TAG, "handleRemoveMember: Error occurred", e)
                Toast.makeText(requireContext(), "Error removing member: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class RemovalResult(val canRemove: Boolean, val newLeaderId: String?)

    private fun handleMakeLeader(userId: String) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "handleMakeLeader: Starting for userId=$userId at ${startTime}")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dbStartTime = System.currentTimeMillis()
                Log.d(TAG, "handleMakeLeader: Starting database operation at ${dbStartTime}")

                databaseService.executeTransactionAsync { realm ->
                    Log.d(TAG, "handleMakeLeader: Inside realm transaction for userId=$userId")
                    makeLeaderSync(realm, userId)
                    Log.d(TAG, "handleMakeLeader: Realm transaction completed")
                }

                val dbEndTime = System.currentTimeMillis()
                Log.d(TAG, "handleMakeLeader: Database operation completed in ${dbEndTime - dbStartTime}ms")

                val adapterStartTime = System.currentTimeMillis()
                Log.d(TAG, "handleMakeLeader: Starting adapter update")

                val currentUserId = user?.id
                adapterJoined?.updateLeadership(currentUserId, userId)

                val adapterEndTime = System.currentTimeMillis()
                Log.d(TAG, "handleMakeLeader: Adapter update completed in ${adapterEndTime - adapterStartTime}ms")

                val toastStartTime = System.currentTimeMillis()
                Toast.makeText(requireContext(), getString(R.string.leader_selected), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "handleMakeLeader: Toast shown in ${System.currentTimeMillis() - toastStartTime}ms")

                val listenerStartTime = System.currentTimeMillis()
                memberChangeListener.onMemberChanged()
                Log.d(TAG, "handleMakeLeader: Member change listener called in ${System.currentTimeMillis() - listenerStartTime}ms")

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "handleMakeLeader: Total operation completed in ${totalTime}ms")

            } catch (e: Exception) {
                Log.e(TAG, "handleMakeLeader: Error occurred", e)
                Toast.makeText(requireContext(), "Error making leader: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun makeLeaderSync(realm: Realm, userId: String) {
        Log.d(TAG, "makeLeaderSync: Starting for userId=$userId")
        val startTime = System.currentTimeMillis()

        val clearStartTime = System.currentTimeMillis()
        val currentLeaders = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findAll()
        Log.d(TAG, "makeLeaderSync: Found ${currentLeaders.size} current leaders")
        currentLeaders.forEach { it.isLeader = false }
        Log.d(TAG, "makeLeaderSync: Cleared existing leaders in ${System.currentTimeMillis() - clearStartTime}ms")

        val setStartTime = System.currentTimeMillis()
        val newLeader = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", userId)
            .findFirst()

        if (newLeader != null) {
            newLeader.isLeader = true
            Log.d(TAG, "makeLeaderSync: Set new leader in ${System.currentTimeMillis() - setStartTime}ms")
        } else {
            Log.w(TAG, "makeLeaderSync: New leader not found for userId=$userId")
        }

        Log.d(TAG, "makeLeaderSync: Completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun removeMemberSync(realm: Realm, userId: String) {
        Log.d(TAG, "removeMemberSync: Starting removal for userId=$userId")
        val startTime = System.currentTimeMillis()

        val team = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", userId)
            .findFirst()

        if (team != null) {
            team.deleteFromRealm()
            Log.d(TAG, "removeMemberSync: Member removed successfully")
        } else {
            Log.w(TAG, "removeMemberSync: Member not found for userId=$userId")
        }

        Log.d(TAG, "removeMemberSync: Completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun getNextOfKinSync(realm: Realm): RealmUserModel? {
        Log.d(TAG, "getNextOfKinSync: Starting next of kin search")
        val startTime = System.currentTimeMillis()

        val membersStartTime = System.currentTimeMillis()
        val members = realm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", false)
            .notEqualTo("status", "archived")
            .findAll()
        Log.d(TAG, "getNextOfKinSync: Found ${members.size} eligible members in ${System.currentTimeMillis() - membersStartTime}ms")

        if (members.isEmpty()) {
            Log.d(TAG, "getNextOfKinSync: No eligible members found")
            return null
        }

        val userIdsStartTime = System.currentTimeMillis()
        val userIds = members.mapNotNull { it.userId }.toTypedArray()
        Log.d(TAG, "getNextOfKinSync: Extracted ${userIds.size} user IDs in ${System.currentTimeMillis() - userIdsStartTime}ms")

        if (userIds.isEmpty()) {
            Log.d(TAG, "getNextOfKinSync: No valid user IDs found")
            return null
        }

        val usersStartTime = System.currentTimeMillis()
        val users = realm.where(RealmUserModel::class.java)
            .`in`("id", userIds)
            .findAll()
        Log.d(TAG, "getNextOfKinSync: Fetched ${users.size} users in ${System.currentTimeMillis() - usersStartTime}ms")

        val mapStartTime = System.currentTimeMillis()
        val userMap = users.associateBy { it.id }
        Log.d(TAG, "getNextOfKinSync: Created user map in ${System.currentTimeMillis() - mapStartTime}ms")

        val selectionStartTime = System.currentTimeMillis()
        val successorMember = members.maxByOrNull { member ->
            userMap[member.userId]?.let { user ->
                val visitCount = RealmTeamLog.getVisitCount(realm, user.name, teamId)
                Log.v(TAG, "getNextOfKinSync: User ${user.name} has visit count: $visitCount")
                visitCount
            } ?: 0L
        }
        Log.d(TAG, "getNextOfKinSync: Selected successor in ${System.currentTimeMillis() - selectionStartTime}ms")

        val result = successorMember?.userId?.let { id ->
            userMap[id]?.let {
                val copyStartTime = System.currentTimeMillis()
                val copy = realm.copyFromRealm(it)
                Log.d(TAG, "getNextOfKinSync: Copied user from realm in ${System.currentTimeMillis() - copyStartTime}ms")
                copy
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "getNextOfKinSync: Completed in ${totalTime}ms, successor: ${result?.name ?: "none"}")
        return result
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}

