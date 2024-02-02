package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.DialogInterface
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
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(private val context: Context, private val list: List<RealmMyTeam>, private val mRealm: Realm, fragmentManager: FragmentManager) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private val user: RealmUserModel = UserProfileDbHandler(context).userModel
    private var type: String? = ""
    private val fragmentManager: FragmentManager
    private var teamListener: OnClickTeamItem? = null

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeam(itemTeamListBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        itemTeamListBinding.created.text = TimeUtils.getFormatedDate(list[position].createdDate)
        itemTeamListBinding.type.text = list[position].teamType
        itemTeamListBinding.type.visibility = if (type == null) View.VISIBLE else View.GONE
        itemTeamListBinding.editTeam.visibility = if (RealmMyTeam.getTeamLeader(list[position]._id, mRealm) == user.id) View.VISIBLE else View.GONE
        itemTeamListBinding.name.text = list[position].name

        itemTeamListBinding.noOfVisits.text = RealmTeamLog.getVisitByTeam(mRealm, list[position]._id).toString() + ""
        val isMyTeam = list[position].isMyTeam(user.id, mRealm)
        showActionButton(isMyTeam, holder, position)
        holder.itemView.setOnClickListener {
            if (context is OnHomeItemClickListener) {
                val f = TeamDetailFragment()
                val b = Bundle()
                b.putString("id", list[position]._id)
                b.putBoolean("isMyTeam", isMyTeam)
                f.arguments = b
                (context as OnHomeItemClickListener).openCallFragment(f)
            }
        }
        itemTeamListBinding.btnFeedback.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.show(fragmentManager, "")
            feedbackFragment.arguments = getBundle(list[position])
        }
        itemTeamListBinding.editTeam.setOnClickListener { teamListener!!.onEditTeam(list[position]) }
    }

    private fun getBundle(team: RealmMyTeam): Bundle {
        val bundle = Bundle()
        if (team.type!!.isEmpty()) bundle.putString("state", "teams") else bundle.putString(
            "state", team.type + "s"
        )
        bundle.putString("item", team._id)
        bundle.putString("parentCode", "dev")
        return bundle
    }

    private fun showActionButton(isMyTeam: Boolean, holder: RecyclerView.ViewHolder, position: Int) {
        if (isMyTeam) {
            if (RealmMyTeam.isTeamLeader(list[position].teamId, user.id!!, mRealm)) {
                itemTeamListBinding.joinLeave.text = "Leave"
                itemTeamListBinding.joinLeave.setOnClickListener {
                    AlertDialog.Builder(context).setMessage(R.string.confirm_exit)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            list[position].leave(user, mRealm)
                            notifyDataSetChanged()
                        }.setNegativeButton(R.string.no, null).show()
                }
            } else {
                itemTeamListBinding.joinLeave.visibility = View.GONE
                return
            }
        } else if (list[position].requested(user.id!!, mRealm)) {
            itemTeamListBinding.joinLeave.text = context.getString(R.string.requested)
            itemTeamListBinding.joinLeave.isEnabled = false
        } else {
            itemTeamListBinding.joinLeave.text = context.getString(R.string.request_to_join)
            itemTeamListBinding.joinLeave.setOnClickListener {
                RealmMyTeam.requestToJoin(list[position]._id, user, mRealm)
                notifyDataSetChanged()
            }
        }
        itemTeamListBinding.joinLeave.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setType(type: String?) {
        this.type = type
    }

    class ViewHolderTeam(itemTeamListBinding: ItemTeamListBinding) : RecyclerView.ViewHolder(itemTeamListBinding.root)

    init {
        this.fragmentManager = fragmentManager
    }
}