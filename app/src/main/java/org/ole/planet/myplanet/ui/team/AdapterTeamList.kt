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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var updateCompleteListener: OnUpdateCompleteListener? = null
    private var filteredList: List<RealmMyTeam> = emptyList()
    private lateinit var prefData: SharedPrefManager
    private val scope = MainScope()
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private var visitCounts: Map<String, Long> = emptyMap()
    private var updateListJob: Job? = null
    private var displayedStatuses: Map<String, TeamStatus> = emptyMap()

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
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(itemTeamListBinding)
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

            showActionButton(teamStatus.isMember, teamStatus.isLeader, teamStatus.hasPendingRequest, team, user)

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

    private fun updateList() {
        val user: RealmUserModel? = currentUser
        val userId = user?.id

        updateListJob?.cancel()
        updateListJob = scope.launch {
            val validTeams = list.filter { it.status?.isNotEmpty() == true }
            if (validTeams.isEmpty()) {
                withContext(Dispatchers.Main) {
                    val oldList = filteredList
                    val oldVisitCounts = visitCounts
                    val oldStatuses = displayedStatuses
                    val newList = emptyList<RealmMyTeam>()
                    val newVisitCounts = emptyMap<String, Long>()
                    val newStatuses = emptyMap<String, TeamStatus>()

                    val (areItemsTheSame, areContentsTheSame) = createTeamDiffCallbacks(
                        oldVisitCounts,
                        newVisitCounts,
                        oldStatuses,
                        newStatuses
                    )

                    val diffResult = DiffUtils.calculateDiff(
                        oldList,
                        newList,
                        areItemsTheSame,
                        areContentsTheSame
                    )

                    visitCounts = newVisitCounts
                    filteredList = newList
                    displayedStatuses = newStatuses

                    diffResult.dispatchUpdatesTo(this@AdapterTeamList)
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

            val newVisitCounts = visitCountsDeferred.await()
            val newStatuses = statusResults.toMap()

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
                    newVisitCounts[team._id.orEmpty()] ?: 0L
                }
            )

            withContext(Dispatchers.Main) {
                val oldList = filteredList
                val oldVisitCounts = visitCounts
                val oldStatuses = displayedStatuses

                val (areItemsTheSame, areContentsTheSame) = createTeamDiffCallbacks(
                    oldVisitCounts,
                    newVisitCounts,
                    oldStatuses,
                    newStatuses
                )

                val diffResult = DiffUtils.calculateDiff(
                    oldList,
                    sortedTeams,
                    areItemsTheSame,
                    areContentsTheSame
                )

                visitCounts = newVisitCounts
                filteredList = sortedTeams
                displayedStatuses = newStatuses

                diffResult.dispatchUpdatesTo(this@AdapterTeamList)
                updateCompleteListener?.onUpdateComplete(filteredList.size)
            }
        }
    }

    private fun createTeamDiffCallbacks(
        oldVisitCounts: Map<String, Long>,
        newVisitCounts: Map<String, Long>,
        oldStatuses: Map<String, TeamStatus>,
        newStatuses: Map<String, TeamStatus>
    ): Pair<(RealmMyTeam, RealmMyTeam) -> Boolean, (RealmMyTeam, RealmMyTeam) -> Boolean> {
        val areItemsTheSame = { oldTeam: RealmMyTeam, newTeam: RealmMyTeam ->
            val oldId = oldTeam._id
            val newId = newTeam._id
            when {
                !oldId.isNullOrBlank() && !newId.isNullOrBlank() -> oldId == newId
                else -> oldTeam == newTeam
            }
        }

        val areContentsTheSame = { oldTeam: RealmMyTeam, newTeam: RealmMyTeam ->
            val teamId = oldTeam._id ?: newTeam._id
            val oldStatus = teamId?.let { oldStatuses[it] }
            val newStatus = teamId?.let { newStatuses[it] }
            val oldVisits = teamId?.let { oldVisitCounts[it] }
            val newVisits = teamId?.let { newVisitCounts[it] }

            oldTeam.name == newTeam.name &&
                oldTeam.createdDate == newTeam.createdDate &&
                oldTeam.teamType == newTeam.teamType &&
                oldTeam.type == newTeam.type &&
                oldTeam.status == newTeam.status &&
                oldStatus == newStatus &&
                oldVisits == newVisits
        }

        return areItemsTheSame to areContentsTheSame
    }


    private fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        val teamType = team.teamType
        val userId = user?.id
        val userPlanetCode = user?.planetCode
        val cacheKey = "${teamId}_${userId}"

        teamStatusCache.remove(cacheKey)

        scope.launch(Dispatchers.IO) {
            teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            withContext(Dispatchers.Main) {
                updateList()
            }
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
            teamRepository.syncTeamActivities(context)
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
    }

    override fun getItemCount(): Int = filteredList.size

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}
