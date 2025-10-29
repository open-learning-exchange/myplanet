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
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val fragmentManager: FragmentManager,
    private val teamRepository: TeamRepository,
    private val currentUser: RealmUserModel?,
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var updateCompleteListener: OnUpdateCompleteListener? = null
    private var filteredList: List<RealmMyTeam> = emptyList()
    private lateinit var prefData: SharedPrefManager
    private val scope = MainScope()
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private val pendingJoinOperations = mutableSetOf<String>()
    private var visitCounts: Map<String, Long> = emptyMap()
    private var updateListJob: Job? = null
    private var debouncedUpdateJob: Job? = null

    data class TeamStatus(
        val isMember: Boolean,
        val isLeader: Boolean,
        val hasPendingRequest: Boolean
    )

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
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
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(binding)
    }

    init {
        updateList()
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = filteredList[position]
        val user: RealmUserModel? = currentUser

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            val visitCount = visitCounts[team._id.orEmpty()] ?: 0L
            noOfVisits.text = context.getString(R.string.number_placeholder, visitCount)

            val teamId = team._id.orEmpty()
            val userId = user?.id
            val cacheKey = "${teamId}_${userId}"
            val teamStatus = teamStatusCache[cacheKey] ?: TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )

            val isJoiningInProgress = pendingJoinOperations.contains(cacheKey)

            showActionButton(
                teamStatus.isMember,
                teamStatus.isLeader,
                teamStatus.hasPendingRequest,
                isJoiningInProgress,
                team,
                user
            )

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
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
        isJoiningInProgress: Boolean,
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

        joinProgress.isVisible = isJoiningInProgress
        joinLeave.alpha = if (isJoiningInProgress) 0.6f else 1f
        if (isJoiningInProgress) {
            joinLeave.isEnabled = false
        }
    }

    private fun handleJoinLeaveClick(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id.orEmpty()
        val userId = user?.id
        val cacheKey = "${teamId}_${userId}"
        val teamStatus = teamStatusCache[cacheKey] ?: TeamStatus(
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
                        leaveTeam(team, userId)
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
            val validTeams = list.filter { it.status?.isNotEmpty() == true }
            if (validTeams.isEmpty()) {
                withContext(Dispatchers.Main) {
                    visitCounts = emptyMap()
                    filteredList = emptyList()
                    notifyDataSetChanged()
                    updateCompleteListener?.onUpdateComplete(filteredList.size)
                }
                return@launch
            }

            val teamIds = validTeams.mapNotNull { it._id?.takeIf { id -> id.isNotBlank() } }

            val visitCountsDeferred = async(Dispatchers.IO) {
                teamRepository.getRecentVisitCounts(teamIds)
            }

            val statusResults = mutableMapOf<String, TeamStatus>()
            val idsToFetch = linkedSetOf<String>()

            validTeams.forEach { team ->
                val teamId = team._id.orEmpty()
                if (teamId.isBlank()) {
                    return@forEach
                }
                val cacheKey = "${teamId}_${userId}"
                val cachedStatus = teamStatusCache[cacheKey]
                if (cachedStatus != null) {
                    statusResults[teamId] = cachedStatus
                } else {
                    idsToFetch += teamId
                }
            }

            if (idsToFetch.isNotEmpty()) {
                idsToFetch.map { teamId ->
                    async(Dispatchers.IO) {
                        val status = TeamStatus(
                            isMember = teamRepository.isMember(userId, teamId),
                            isLeader = teamRepository.isTeamLeader(teamId, userId),
                            hasPendingRequest = teamRepository.hasPendingRequest(teamId, userId),
                        )
                        teamId to status
                    }
                }.awaitAll().forEach { (teamId, status) ->
                    val cacheKey = "${teamId}_${userId}"
                    teamStatusCache[cacheKey] = status
                    statusResults[teamId] = status
                }
            }

            val visitCounts = visitCountsDeferred.await()

            val sortedTeams = validTeams.sortedWith(
                compareByDescending<RealmMyTeam> { team ->
                    val teamId = team._id.orEmpty()
                    val status = statusResults[teamId]
                        ?: TeamStatus(isMember = false, isLeader = false, hasPendingRequest = false)
                    when {
                        status.isLeader -> 3
                        status.isMember -> 2
                        else -> 1
                    }
                }.thenByDescending { team ->
                    visitCounts[team._id.orEmpty()] ?: 0L
                }
            )

            withContext(Dispatchers.Main) {
                this@AdapterTeamList.visitCounts = visitCounts
                filteredList = sortedTeams
                notifyDataSetChanged()
                updateCompleteListener?.onUpdateComplete(filteredList.size)
            }
        }
    }


    private fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        val teamType = team.teamType
        val userId = user?.id
        val userPlanetCode = user?.planetCode
        val cacheKey = "${teamId}_${userId}"

        val existingStatus = teamStatusCache[cacheKey]
        val updatedStatus = TeamStatus(
            isMember = existingStatus?.isMember ?: false,
            isLeader = existingStatus?.isLeader ?: false,
            hasPendingRequest = true
        )
        teamStatusCache[cacheKey] = updatedStatus
        pendingJoinOperations += cacheKey

        val position = filteredList.indexOfFirst { it._id == teamId }
        if (position != -1) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }

        scope.launch(Dispatchers.IO) {
            try {
                teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            } finally {
                withContext(Dispatchers.Main) {
                    pendingJoinOperations.remove(cacheKey)
                    scheduleUpdateListDebounced()
                }
            }
        }
    }

    private fun scheduleUpdateListDebounced(delayMillis: Long = 300L) {
        debouncedUpdateJob?.cancel()
        debouncedUpdateJob = scope.launch {
            delay(delayMillis)
            updateList()
        }
    }

    private fun leaveTeam(team: RealmMyTeam, userId: String?) {
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
        MainApplication.applicationScope.launch {
            teamRepository.syncTeamActivities()
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

    fun cleanup() {
        scope.cancel()
        teamStatusCache.clear()
        pendingJoinOperations.clear()
        debouncedUpdateJob?.cancel()
    }

    override fun getItemCount(): Int = filteredList.size

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}
