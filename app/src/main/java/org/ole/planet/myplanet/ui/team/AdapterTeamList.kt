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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val fragmentManager: FragmentManager,
    private val uploadManager: UploadManager,
    private val teamRepository: TeamRepository
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private lateinit var prefData: SharedPrefManager

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(itemTeamListBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = list[position]
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel
        val lifecycleOwner = context as LifecycleOwner

        lifecycleOwner.lifecycleScope.launch {
            val teamId = team._id.orEmpty()
            val isMyTeam = user?.id?.let { teamRepository.isMyTeam(teamId, it) } ?: false
            val visitCount = teamRepository.getVisitCountForTeam(teamId)

            with(holder.binding) {
                created.text = TimeUtils.getFormattedDate(team.createdDate)
                type.text = team.teamType
                type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
                name.text = team.name
                noOfVisits.text = context.getString(R.string.number_placeholder, visitCount)

                showActionButton(isMyTeam, team, user)

                root.setOnClickListener {
                    val activity = context as? AppCompatActivity ?: return@setOnClickListener
                      val fragment = TeamDetailFragment.newInstance(
                          teamId = teamId,
                          teamName = team.name.orEmpty(),
                          teamType = team.type.orEmpty(),
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
                    FeedbackFragment().apply {
                        arguments = getBundle(team)
                    }.show(fragmentManager, "")
                }

                joinLeave.setOnClickListener {
                    handleJoinLeaveClick(isMyTeam, team, user)
                }
            }
        }
    }

    private fun ItemTeamListBinding.showActionButton(isMyTeam: Boolean, team: RealmMyTeam, user: RealmUserModel?) {
        val lifecycleOwner = context as LifecycleOwner
        lifecycleOwner.lifecycleScope.launch {
            name.setTypeface(null, if (isMyTeam) Typeface.BOLD else Typeface.NORMAL)
              val teamId = team._id.orEmpty()
              val teamLeaderId = teamRepository.getTeamLeaderId(teamId)
              val hasPendingRequest = user?.id?.let { teamRepository.hasPendingRequest(teamId, it) } ?: false

            when {
                user?.isGuest() == true -> joinLeave.visibility = View.GONE
                isMyTeam && teamLeaderId != user?.id -> {
                    joinLeave.apply {
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
                teamLeaderId == user?.id -> {
                    joinLeave.apply {
                        contentDescription = "${context.getString(R.string.edit)} ${team.name}"
                        visibility = View.VISIBLE
                        setImageResource(R.drawable.ic_edit)
                        clearColorFilter()
                    }
                }
                else -> joinLeave.visibility = View.GONE
            }
        }
    }

    private fun handleJoinLeaveClick(isMyTeam: Boolean, team: RealmMyTeam, user: RealmUserModel?) {
        val lifecycleOwner = context as LifecycleOwner
        lifecycleOwner.lifecycleScope.launch {
              val teamId = team._id.orEmpty()
              val isLeader = user?.id?.let { teamRepository.isTeamLeader(teamId, it) } ?: false
            if (isMyTeam) {
                if (isLeader) {
                    teamListener?.onEditTeam(team)
                } else {
                    AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                              lifecycleOwner.lifecycleScope.launch {
                                  user?.id?.let { teamRepository.leaveTeam(teamId, it) }
                                  notifyDataSetChanged() // Or use a callback to the fragment to reload
                              }
                        }.setNegativeButton(R.string.no, null).show()
                }
            } else {
                  lifecycleOwner.lifecycleScope.launch {
                      user?.id?.let { teamRepository.requestToJoin(teamId, it, team.teamType ?: "local") }
                      notifyDataSetChanged() // Or use a callback
                  }
            }
            // syncTeamActivities(context, uploadManager) // This needs to be handled in the repository or fragment
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

    override fun getItemCount(): Int = list.size

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}
