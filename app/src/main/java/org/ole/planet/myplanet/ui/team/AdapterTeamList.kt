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
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val fragmentManager: FragmentManager,
    private val teamRepository: TeamRepository,
    private val currentUser: RealmUserModel?,
    private val scope: CoroutineScope,
    private val sharedPrefManager: SharedPrefManager
) : ListAdapter<TeamData, AdapterTeamList.ViewHolderTeam>(TeamDiffCallback) {
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var updateCompleteListener: OnUpdateCompleteListener? = null
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private val visitCountsCache = mutableMapOf<String, Long>()
    private var visitCounts: Map<String, Long> = emptyMap()
    private var updateListJob: Job? = null
    private var syncJob: Job? = null

    interface OnClickTeamItem {
        fun onEditTeam(team: TeamData?)
    }

    interface OnUpdateCompleteListener {
        fun onUpdateComplete(itemCount: Int)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    fun setUpdateCompleteListener(listener: OnUpdateCompleteListener?) {
        this.updateCompleteListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderTeam(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = getItem(position)
        val user: RealmUserModel? = currentUser

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate ?: 0)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, team.visitCount)

            val teamId = team._id.orEmpty()
            val teamStatus = team.teamStatus ?: TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )

            showActionButton(teamStatus.isMember, teamStatus.isLeader, teamStatus.hasPendingRequest, team, user)

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = teamId,
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = teamStatus.isMember
                )
                NavigationHelper.replaceFragment(
                    activity.supportFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    tag = "TeamDetailFragment"
                )
                sharedPrefManager.setTeamName(team.name)
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
        team: TeamData,
        user: RealmUserModel?,
    ) {
        if (isMyTeam) {
            name.setTypeface(null, Typeface.BOLD)
        } else {
            name.setTypeface(null, Typeface.NORMAL)
        }
        when {
            user?.isGuest() == true -> joinLeave.visibility = View.GONE

            isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.edit)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_edit)
                    clearColorFilter()
                }
            }

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

            else -> joinLeave.visibility = View.GONE
        }
    }

    private fun handleJoinLeaveClick(team: TeamData, user: RealmUserModel?) {
        val teamStatus = team.teamStatus ?: TeamStatus(
            isMember = false,
            isLeader = false,
            hasPendingRequest = false
        )

        if (teamStatus.isMember) {
            if (teamStatus.isLeader) {
                teamListener?.onEditTeam(team)
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        leaveTeam(team, user?.id)
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            requestToJoin(team, user)
        }
        syncTeamActivities()
    }

    fun updateList() {
        val user: RealmUserModel? = currentUser
        val userId = user?.id

        updateListJob?.cancel()
        updateListJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                val validTeams = list.filter {
                    !it._id.isNullOrBlank() && (it.status == null || it.status != "archived")
                }

                if (validTeams.isEmpty()) {
                    return@withContext null
                }

                val teamIds = validTeams.mapNotNull { it._id?.takeIf { id -> id.isNotBlank() } }
                val (cachedVisitIds, nonCachedVisitIds) = teamIds.partition { it in visitCountsCache }

                val visitCountsDeferred = if (nonCachedVisitIds.isNotEmpty()) {
                    async(Dispatchers.IO) {
                        teamRepository.getRecentVisitCounts(nonCachedVisitIds)
                    }
                } else {
                    async { emptyMap<String, Long>() }
                }

                val statusResults = mutableMapOf<String, TeamStatus>()
                val idsToFetch = linkedSetOf<String>()
                validTeams.forEach { team ->
                    val teamId = team._id.orEmpty()
                    if (teamId.isBlank()) return@forEach
                    val cacheKey = "${teamId}_${userId}"
                    val cachedStatus = teamStatusCache[cacheKey]
                    if (cachedStatus != null) {
                        statusResults[teamId] = cachedStatus
                    } else {
                        idsToFetch += teamId
                    }
                }

                if (idsToFetch.isNotEmpty()) {
                    val batchStatuses = withContext(Dispatchers.IO) {
                        teamRepository.getTeamMemberStatuses(userId, idsToFetch)
                    }

                    batchStatuses.forEach { (teamId, memberStatus) ->
                        val status = TeamStatus(
                            isMember = memberStatus.isMember,
                            isLeader = memberStatus.isLeader,
                            hasPendingRequest = memberStatus.hasPendingRequest
                        )
                        val cacheKey = "${teamId}_${userId}"
                        teamStatusCache[cacheKey] = status
                        statusResults[teamId] = status
                    }
                }

                val newVisitCounts = visitCountsDeferred.await()
                newVisitCounts.forEach { (id, count) -> visitCountsCache[id] = count }
                val calculatedVisitCounts =
                    cachedVisitIds.associateWith { visitCountsCache[it]!! } + newVisitCounts

                val sortedTeams = validTeams.sortedWith(
                    compareByDescending<RealmMyTeam> { team ->
                        val teamId = team._id.orEmpty()
                        val status = statusResults[teamId] ?: TeamStatus(false, false, false)
                        when {
                            status.isLeader -> 3
                            status.isMember -> 2
                            else -> 1
                        }
                    }.thenByDescending { team ->
                        calculatedVisitCounts[team._id.orEmpty()] ?: 0L
                    }
                )

                val newList = sortedTeams.map { team ->
                    val teamId = team._id.orEmpty()
                    val cacheKey = "${teamId}_${userId}"
                    TeamData(
                        _id = team._id,
                        name = team.name,
                        teamType = team.teamType,
                        createdDate = team.createdDate,
                        type = team.type,
                        status = team.status,
                        visitCount = calculatedVisitCounts[teamId] ?: 0L,
                        teamStatus = teamStatusCache[cacheKey],
                        description = team.description,
                        services = team.services,
                        rules = team.rules,
                        teamId = team.teamId
                    )
                }
                Pair(newList, calculatedVisitCounts)
            }

            if (result == null) {
                visitCounts = emptyMap()
                submitList(emptyList()) {
                    updateCompleteListener?.onUpdateComplete(0)
                }
            } else {
                val (newList, allVisitCounts) = result
                visitCounts = allVisitCounts
                submitList(newList) {
                    updateCompleteListener?.onUpdateComplete(newList.size)
                }
            }
        }
    }

    private fun requestToJoin(team: TeamData, user: RealmUserModel?) {
        val teamId = team._id ?: return
        val teamType = team.teamType
        val userId = user?.id
        val userPlanetCode = user?.planetCode
        val cacheKey = "${teamId}_${userId}"

        val newStatus = TeamStatus(
            isMember = false,
            isLeader = false,
            hasPendingRequest = true
        )
        teamStatusCache[cacheKey] = newStatus

        // Optimistic update
        val updatedList = currentList.map {
            if (it._id == teamId) it.copy(teamStatus = newStatus) else it
        }
        submitList(updatedList)

        scope.launch(Dispatchers.IO) {
            teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            withContext(Dispatchers.Main) {
                teamStatusCache.remove(cacheKey)
                updateList()
            }
        }
    }

    private fun leaveTeam(team: TeamData, userId: String?) {
        val teamId = team._id ?: return
        val cacheKey = "${teamId}_${userId}"
        teamStatusCache.remove(cacheKey)

        scope.launch(Dispatchers.IO) {
            teamRepository.leaveTeam(teamId, userId)
            withContext(Dispatchers.Main) {
                updateList()
            }
        }
    }

    private fun syncTeamActivities() {
        syncJob?.cancel()
        syncJob = scope.launch {
            teamRepository.syncTeamActivities()
        }
    }

    private fun getBundle(team: TeamData): Bundle {
        return Bundle().apply {
            putString("state", if (team.type?.isEmpty() == true) "teams" else "${team.type}s")
            putString("item", team._id)
            putString("parentCode", "dev")
        }
    }

    fun setType(type: String?) {
        this.type = type
    }

    fun cleanup() {
        scope.cancel()
        teamStatusCache.clear()
        visitCountsCache.clear()
    }

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val TeamDiffCallback = DiffUtils.itemCallback<TeamData>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
