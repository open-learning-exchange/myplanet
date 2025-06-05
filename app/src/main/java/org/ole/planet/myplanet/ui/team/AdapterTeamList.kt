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
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils
import androidx.core.graphics.toColorInt
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.syncTeamActivities

class AdapterTeamList(private val context: Context, private val list: List<RealmMyTeam>, private val mRealm: Realm, private val fragmentManager: FragmentManager) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var filteredList: List<RealmMyTeam> = emptyList()
    private lateinit var prefData: SharedPrefManager

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

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = filteredList[position]
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel

        with(holder.binding) {
            created.text = TimeUtils.getFormatedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, RealmTeamLog.getVisitByTeam(mRealm, team._id))

            val isMyTeam = team.isMyTeam(user?.id, mRealm)
            showActionButton(isMyTeam, team, user)

//            root.setOnClickListener {
//                if (context is OnHomeItemClickListener) {
//                    val fragmentManager = (context as AppCompatActivity).supportFragmentManager
//                    val existingFragment = fragmentManager.findFragmentByTag("TeamDetailFragment")
//                    val b = Bundle()
//                    b.putString("id", team._id)
//                    b.putBoolean("isMyTeam", isMyTeam)
//                    if (existingFragment is TeamDetailFragment) {
//                        existingFragment.arguments?.clear()
//                        existingFragment.arguments = b
//                    }
//                    val f = TeamDetailFragment()
//                    f.arguments = b
//                    (context as OnHomeItemClickListener).openCallFragment(f)
//                    prefData.setTeamName(team.name)
//                }
//            }
            root.setOnClickListener {
                if (context is OnHomeItemClickListener) {
                    val fragmentManager = (context as AppCompatActivity).supportFragmentManager
                    val existingFragment = fragmentManager.findFragmentByTag("TeamDetailFragment")

                    // Use new optimized approach
                    val f = TeamDetailFragment.newInstance(
                        teamId = "${team._id}",
                        teamName = "${team.name}",
                        teamType = "${team.type}",
                        isMyTeam = isMyTeam
                    )

                    // Keep existing fragment replacement logic if needed
                    if (existingFragment is TeamDetailFragment) {
                        existingFragment.arguments?.clear()
                        existingFragment.arguments = f.arguments
                    }

                    (context as OnHomeItemClickListener).openCallFragment(f)
                    prefData.setTeamName(team.name)
                }
            }


            btnFeedback.setOnClickListener {
                val feedbackFragment = FeedbackFragment()
                feedbackFragment.show(fragmentManager, "")
                feedbackFragment.arguments = getBundle(team)
            }

            joinLeave.setOnClickListener {
                handleJoinLeaveClick(isMyTeam, team, user)
            }
        }
    }

    private fun ItemTeamListBinding.showActionButton(isMyTeam: Boolean, team: RealmMyTeam, user: RealmUserModel?) {
        if (isMyTeam) {
            name.setTypeface(null, Typeface.BOLD)
        } else {
            name.setTypeface(null, Typeface.NORMAL)
        }
        when {
            user?.isGuest() == true -> joinLeave.visibility = View.GONE

            isMyTeam && RealmMyTeam.getTeamLeader(team._id, mRealm) != user?.id -> {
                joinLeave.apply {
                    contentDescription = "${context.getString(R.string.leave)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.logout)
                    clearColorFilter()
                }
            }

            !isMyTeam && team.requested(user?.id, mRealm) -> {
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

            RealmMyTeam.getTeamLeader(team._id, mRealm) == user?.id -> {
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

    private fun handleJoinLeaveClick(isMyTeam: Boolean, team: RealmMyTeam, user: RealmUserModel?) {
        if (isMyTeam) {
            if (RealmMyTeam.isTeamLeader(team._id, user?.id, mRealm)) {
                teamListener?.onEditTeam(team)
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        team.leave(user, mRealm)
                        updateList()
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            RealmMyTeam.requestToJoin(team._id, user, mRealm, team.teamType)
            updateList()
        }
        syncTeamActivities(context)
    }

    private fun updateList() {
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel
        val userId = user?.id

        val validTeams = list.filter { it.status?.isNotEmpty() == true }
        filteredList = validTeams.sortedWith(compareByDescending<RealmMyTeam> { team ->
            when {
                userId != null && RealmMyTeam.isTeamLeader(team._id, userId, mRealm) -> 3
                team.isMyTeam(userId, mRealm) -> 2
                else -> 1
            }
        }.thenByDescending { team ->
            RealmTeamLog.getVisitByTeam(mRealm, team._id)
        })
        notifyDataSetChanged()
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
