package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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

class AdapterTeamList(private val context: Context, private val list: List<RealmMyTeam>, private val mRealm: Realm,
                      private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private val user: RealmUserModel? = UserProfileDbHandler(context).userModel
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var filteredList: List<RealmMyTeam> = list.filter { it.status!!.isNotEmpty() }
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

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        itemTeamListBinding.created.text = TimeUtils.getFormatedDate(filteredList[position].createdDate)
        itemTeamListBinding.type.text = filteredList[position].teamType
        itemTeamListBinding.type.visibility =
            if (type == null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        itemTeamListBinding.editTeam.visibility =
            if (RealmMyTeam.getTeamLeader(filteredList[position]._id, mRealm) == user?.id) {
                View.VISIBLE
            } else {
                View.GONE
            }
        itemTeamListBinding.name.text = filteredList[position].name
        itemTeamListBinding.noOfVisits.text = context.getString(R.string.number_placeholder, RealmTeamLog.getVisitByTeam(mRealm, filteredList[position]._id))
        val isMyTeam = filteredList[position].isMyTeam(user?.id, mRealm)
        showActionButton(isMyTeam, position)
        holder.itemView.setOnClickListener {
            if (context is OnHomeItemClickListener) {
                val f = TeamDetailFragment()
                val b = Bundle()
                b.putString("id", filteredList[position]._id)
                b.putBoolean("isMyTeam", isMyTeam)
                f.arguments = b
                (context as OnHomeItemClickListener).openCallFragment(f)
                prefData.setTeamName(filteredList[position].name)
            }
        }
        itemTeamListBinding.btnFeedback.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.show(fragmentManager, "")
            feedbackFragment.arguments = getBundle(list[position])
        }
        itemTeamListBinding.editTeam.setOnClickListener { teamListener?.onEditTeam(list[position]) }

        itemTeamListBinding.joinLeave.setOnClickListener {
            if (isMyTeam) {
                if (RealmMyTeam.isTeamLeader(list[position].teamId, user?.id, mRealm)) {
                    AlertDialog.Builder(context).setMessage(R.string.confirm_exit)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            list[position].leave(user, mRealm)
                        }.setNegativeButton(R.string.no, null).show()
                }
            } else {
                RealmMyTeam.requestToJoin(list[position]._id, user, mRealm)
            }
            it.isEnabled = false
        }
    }

    private fun getBundle(team: RealmMyTeam): Bundle {
        val bundle = Bundle()
        if (team.type?.isEmpty() == true) {
            bundle.putString("state", "teams")
        } else {
            bundle.putString("state", "${team.type}s")
        }
        bundle.putString("item", team._id)
        bundle.putString("parentCode", "dev")
        return bundle
    }

    private fun showActionButton(isMyTeam: Boolean, position: Int) {
        if (user?.isGuest() == true) {
            itemTeamListBinding.joinLeave.visibility = View.GONE
            return
        }
        if (isMyTeam) {
            if (RealmMyTeam.isTeamLeader(filteredList[position].teamId, user?.id, mRealm)) {
                itemTeamListBinding.joinLeave.contentDescription = "${context.getString(R.string.leave)} ${filteredList[position].name}"
                itemTeamListBinding.joinLeave.visibility = View.VISIBLE
            } else {
                itemTeamListBinding.joinLeave.visibility = View.GONE
            }
        } else if (filteredList[position].requested(user?.id, mRealm)) {
            itemTeamListBinding.joinLeave.isEnabled = false
            itemTeamListBinding.joinLeave.contentDescription = "${context.getString(R.string.requested)} ${filteredList[position].name}"
            itemTeamListBinding.joinLeave.visibility = View.VISIBLE
            itemTeamListBinding.joinLeave.setImageResource(R.drawable.baseline_hourglass_top_24)
            itemTeamListBinding.joinLeave.setColorFilter(Color.parseColor("#9fa0a4"), PorterDuff.Mode.SRC_IN)
        } else {
            itemTeamListBinding.joinLeave.contentDescription = "${context.getString(R.string.request_to_join)} ${filteredList[position].name}"
            itemTeamListBinding.joinLeave.visibility = View.VISIBLE
        }
    }


    override fun getItemCount(): Int {
        return filteredList.size
    }

    fun setType(type: String?) {
        this.type = type
    }

    class ViewHolderTeam(itemTeamListBinding: ItemTeamListBinding) : RecyclerView.ViewHolder(itemTeamListBinding.root)
}