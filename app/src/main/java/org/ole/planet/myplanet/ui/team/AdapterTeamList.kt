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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val coroutineScope: CoroutineScope,
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var dataLoadedCallback: OnDataLoadedCallback? = null
    private var filteredList: List<RealmMyTeam> = list
    private val teamDataCache = mutableMapOf<String, TeamMembershipData>()
    private lateinit var prefData: SharedPrefManager

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    interface OnDataLoadedCallback {
        fun onDataLoaded(hasData: Boolean)
        fun onLoadingStarted()
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    fun setDataLoadedCallback(callback: OnDataLoadedCallback?) {
        this.dataLoadedCallback = callback
        if (filteredList.isEmpty()) {
            callback?.onLoadingStarted()
        } else {
            callback?.onDataLoaded(filteredList.isNotEmpty())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(itemTeamListBinding)
    }

    init {
        filteredList = emptyList()

        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                dataLoadedCallback?.onLoadingStarted()
            }
            updateListAsync()
        }
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

            val cachedData = teamDataCache[teamId]
            if (cachedData != null) {
                showActionButton(cachedData.isMember, cachedData.isLeader, false, team, user)
            } else {
                showActionButton(false, false, false, team, user)
                coroutineScope.launch {
                    val isMyTeam = isMemberOfTeam(teamId, userId)
                    val isTeamLeader = isUserTeamLeader(teamId, userId)
                    val hasPendingRequest = hasPendingRequest(teamId, userId)

                    withContext(Dispatchers.Main) {
                        showActionButton(isMyTeam, isTeamLeader, hasPendingRequest, team, user)
                    }
                }
            }

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val isMyTeam = cachedData?.isMember ?: false
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = isMyTeam
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
        val teamId = team._id
        val userId = user?.id
        coroutineScope.launch {
            val isMyTeam = isMemberOfTeam(teamId, userId)
            val isTeamLeader = if (isMyTeam) isUserTeamLeader(teamId, userId) else false

            withContext(Dispatchers.Main) {
                if (isMyTeam) {
                    if (isTeamLeader) {
                        teamListener?.onEditTeam(team)
                    } else {
                        AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                            .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                                leaveTeam(team, userId)
                                coroutineScope.launch { updateList() }
                            }.setNegativeButton(R.string.no, null).show()
                    }
                } else {
                    requestToJoin(team, user)
                    coroutineScope.launch { updateList() }
                }
                syncTeamActivities()
            }
        }
    }

    private suspend fun updateListAsync() {
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel
        val userId = user?.id
        val validTeams = list.filter { it.status?.isNotEmpty() == true }

        coroutineScope {
            val teamDataJobs = validTeams.mapNotNull { team ->
                val teamId = team._id ?: return@mapNotNull null
                if (teamDataCache.containsKey(teamId)) return@mapNotNull null

                async {
                    val isLeader = isUserTeamLeader(teamId, userId)
                    val isMember = isMemberOfTeam(teamId, userId)
                    val visitCount = RealmTeamLog.getVisitByTeam(mRealm, teamId).toInt()

                    teamId to TeamMembershipData(isLeader, isMember, visitCount)
                }
            }

            teamDataJobs.awaitAll().forEach { (teamId, data) ->
                teamDataCache[teamId] = data
            }
        }

        filteredList = validTeams.sortedWith(compareByDescending<RealmMyTeam> { team ->
            val data = teamDataCache[team._id]
            when {
                data?.isLeader == true -> 3
                data?.isMember == true -> 2
                else -> 1
            }
        }.thenByDescending { team ->
            teamDataCache[team._id]?.visitCount ?: 0
        })

        withContext(Dispatchers.Main) {
            notifyDataSetChanged()
            dataLoadedCallback?.onDataLoaded(filteredList.isNotEmpty())
        }
    }
    fun updateList() {
        coroutineScope.launch {
            updateListAsync()
        }
    }

    private suspend fun isMemberOfTeam(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return teamRepository.isMember(userId, teamId)
    }

    private suspend fun isUserTeamLeader(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return teamRepository.isTeamLeader(teamId, userId)
    }

    private suspend fun hasPendingRequest(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return teamRepository.hasPendingRequest(teamId, userId)
    }

    private fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        coroutineScope.launch {
            teamRepository.requestToJoin(teamId, user, team.teamType)
        }
    }

    private fun leaveTeam(team: RealmMyTeam, userId: String?) {
        val teamId = team._id ?: return
        coroutineScope.launch {
            teamRepository.leaveTeam(teamId, userId)
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

    override fun getItemCount(): Int = filteredList.size

    private data class TeamMembershipData(
        val isLeader: Boolean,
        val isMember: Boolean,
        val visitCount: Int
    )

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}

