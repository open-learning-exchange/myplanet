package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val mRealm: Realm,
    private val fragmentManager: FragmentManager,
    private val teamRepository: TeamRepository,
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var filteredList: MutableList<RealmMyTeam> = mutableListOf()
    private lateinit var prefData: SharedPrefManager
    private var currentUserId: String? = null
    private var scopeJob: Job = SupervisorJob()
    private var adapterScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + scopeJob)
    private val teamStateCache = mutableMapOf<String, TeamMembershipState>()
    private val teamStateJobs = mutableMapOf<String, Job>()

    private data class TeamMembershipState(
        val isMember: Boolean = false,
        val isLeader: Boolean = false,
        val hasPendingRequest: Boolean = false,
    )

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(itemTeamListBinding)
    }

    init {
        updateList()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!scopeJob.isActive) {
            scopeJob = SupervisorJob()
            adapterScope = CoroutineScope(Dispatchers.Main.immediate + scopeJob)
            refreshAllTeamStates(force = true)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scopeJob.cancel()
        teamStateJobs.clear()
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = filteredList[position]
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, RealmTeamLog.getVisitByTeam(mRealm, team._id))

            val teamId = team._id
            val userId = user?.id
            val state = teamStateCache[teamId] ?: TeamMembershipState()
            showActionButton(state.isMember, state.isLeader, state.hasPendingRequest, team, user)
            if (!teamStateCache.containsKey(teamId)) {
                refreshTeamState(teamId, userId, force = false)
            }

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = state.isMember
                )
                NavigationHelper.replaceFragment(
                    activity.supportFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    tag = "TeamDetailFragment"
                )
                prefData.setTeamName(team.name)
            }

            btnFeedback.setOnClickListener {
                val feedbackFragment = FeedbackFragment()
                feedbackFragment.show(fragmentManager, "")
                feedbackFragment.arguments = getBundle(team)
            }

            joinLeave.setOnClickListener {
                handleJoinLeaveClick(team, user)
            }
        }
    }

    private fun ItemTeamListBinding.showActionButton(
        isMyTeam: Boolean,
        isTeamLeader: Boolean,
        hasPendingRequest: Boolean,
        team: RealmMyTeam,
        user: RealmUserModel?,
    ) {
        if (isMyTeam) {
            name.setTypeface(null, Typeface.BOLD)
        } else {
            name.setTypeface(null, Typeface.NORMAL)
        }
        when {
            user?.isGuest() == true -> joinLeave.visibility = View.GONE

            isMyTeam && !isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.leave)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.logout)
                    clearColorFilter()
                }
            }

            !isMyTeam && hasPendingRequest -> {
                joinLeave.apply {
                    isEnabled = false
                    contentDescription = "${context.getString(R.string.requested)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.baseline_hourglass_top_24)
                    setColorFilter("#9fa0a4".toColorInt(), PorterDuff.Mode.SRC_IN)
                }
            }

            !isMyTeam -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.request_to_join)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_join_request)
                    clearColorFilter()
                }
            }

            isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.edit)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_edit)
                    clearColorFilter()
                }
            }

            else -> joinLeave.visibility = View.GONE
        }
    }

    private fun handleJoinLeaveClick(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        val userId = user?.id
        val state = teamStateCache[teamId] ?: TeamMembershipState()
        if (state.isMember) {
            if (state.isLeader) {
                teamListener?.onEditTeam(team)
                syncTeamActivities()
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        adapterScope.launch {
                            leaveTeam(team, userId)
                            invalidateTeamState(teamId)
                            refreshTeamState(teamId, userId, force = true)
                            syncTeamActivities()
                        }
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            adapterScope.launch {
                requestToJoin(team, user)
                invalidateTeamState(teamId)
                refreshTeamState(teamId, userId, force = true)
                syncTeamActivities()
            }
        }
    }

    private fun updateList() {
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel
        val userId = user?.id
        currentUserId = userId

        val validTeams = list.filter { it.status?.isNotEmpty() == true }
        val currentIds = validTeams.mapNotNull { it._id }.toSet()
        teamStateCache.keys.retainAll(currentIds)
        val iterator = teamStateJobs.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in currentIds) {
                entry.value.cancel()
                iterator.remove()
            }
        }
        filteredList = validTeams.sortedWith(teamComparator).toMutableList()
        notifyDataSetChanged()
        refreshAllTeamStates(force = false)
    }

    private suspend fun isMemberOfTeam(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank()) return false
        return withContext(Dispatchers.IO) { teamRepository.isMember(userId, teamId) }
    }

    private suspend fun isUserTeamLeader(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank()) return false
        return withContext(Dispatchers.IO) { teamRepository.isTeamLeader(teamId, userId) }
    }

    private suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean {
        if (teamId.isBlank()) return false
        return withContext(Dispatchers.IO) { teamRepository.hasPendingRequest(teamId, userId) }
    }

    private suspend fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id?.takeIf { it.isNotBlank() } ?: return
        withContext(Dispatchers.IO) { teamRepository.requestToJoin(teamId, user, team.teamType) }
    }

    private suspend fun leaveTeam(team: RealmMyTeam, userId: String?) {
        val teamId = team._id?.takeIf { it.isNotBlank() } ?: return
        withContext(Dispatchers.IO) { teamRepository.leaveTeam(teamId, userId) }
    }

    private fun syncTeamActivities() {
        MainApplication.applicationScope.launch {
            teamRepository.syncTeamActivities(context)
        }
    }

    private fun refreshAllTeamStates(force: Boolean) {
        val userId = currentUserId
        filteredList.forEach { team ->
            refreshTeamState(team._id, userId, force)
        }
    }

    private fun refreshTeamState(teamId: String?, userId: String?, force: Boolean) {
        val id = teamId ?: return
        if (id.isBlank()) return
        if (!force && teamStateCache.containsKey(id)) return
        teamStateJobs[id]?.cancel()
        val job = adapterScope.launch {
            val isMember = isMemberOfTeam(id, userId)
            val isLeader = isUserTeamLeader(id, userId)
            val hasPendingRequest = hasPendingRequest(id, userId)
            val state = TeamMembershipState(isMember, isLeader, hasPendingRequest)
            updateCachedState(id, state)
        }
        job.invokeOnCompletion { teamStateJobs.remove(id) }
        teamStateJobs[id] = job
    }

    private fun invalidateTeamState(teamId: String) {
        teamStateCache.remove(teamId)
        teamStateJobs[teamId]?.cancel()
        teamStateJobs.remove(teamId)
        resortTeam(teamId)
    }

    private fun updateCachedState(teamId: String, state: TeamMembershipState) {
        val previousState = teamStateCache[teamId]
        if (previousState == state) return
        teamStateCache[teamId] = state
        resortTeam(teamId)
    }

    private fun resortTeam(teamId: String) {
        val currentIndex = filteredList.indexOfFirst { it._id == teamId }
        if (currentIndex == -1) return
        val team = filteredList[currentIndex]
        val mutableList = filteredList.toMutableList()
        mutableList.removeAt(currentIndex)
        var targetIndex = 0
        while (targetIndex < mutableList.size) {
            val other = mutableList[targetIndex]
            if (shouldPlaceBefore(team, other)) {
                break
            }
            targetIndex++
        }
        mutableList.add(targetIndex, team)
        filteredList = mutableList
        if (currentIndex != targetIndex) {
            notifyItemMoved(currentIndex, targetIndex)
            notifyItemChanged(targetIndex)
            notifyItemChanged(currentIndex)
        } else {
            notifyItemChanged(currentIndex)
        }
    }

    private val teamComparator = Comparator<RealmMyTeam> { first, second ->
        val firstPriority = getTeamPriority(first)
        val secondPriority = getTeamPriority(second)
        when {
            firstPriority != secondPriority -> secondPriority.compareTo(firstPriority)
            else -> {
                val firstVisits = RealmTeamLog.getVisitByTeam(mRealm, first._id)
                val secondVisits = RealmTeamLog.getVisitByTeam(mRealm, second._id)
                secondVisits.compareTo(firstVisits)
            }
        }
    }

    private fun shouldPlaceBefore(team: RealmMyTeam, other: RealmMyTeam): Boolean {
        val teamPriority = getTeamPriority(team)
        val otherPriority = getTeamPriority(other)
        return when {
            teamPriority != otherPriority -> teamPriority > otherPriority
            else -> {
                val teamVisits = RealmTeamLog.getVisitByTeam(mRealm, team._id)
                val otherVisits = RealmTeamLog.getVisitByTeam(mRealm, other._id)
                teamVisits > otherVisits
            }
        }
    }

    private fun getTeamPriority(team: RealmMyTeam): Int {
        val state = teamStateCache[team._id] ?: TeamMembershipState()
        return when {
            state.isLeader -> 3
            state.isMember -> 2
            else -> 1
        }
    }

    private fun getBundle(team: RealmMyTeam): Bundle {
        return Bundle().apply {
            putString("state", if (team.type?.isEmpty() == true) "teams" else "${team.type}s")
            putString("item", team._id)
            putString("parentCode", "dev")
        }
    }

    fun setType(type: String?) {
        this.type = type
    }

    override fun getItemCount(): Int = filteredList.size

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}
