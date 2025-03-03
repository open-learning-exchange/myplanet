package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

class AdapterMemberRequest(private val context: Context, private val list: MutableList<RealmUserModel>, private val mRealm: Realm, private val isTeamLeader: Boolean) : RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser>() {
    private lateinit var rowMemberRequestBinding: RowMemberRequestBinding
    private var teamId: String? = null
    private lateinit var team: RealmMyTeam

    fun setTeamId(teamId: String?) {
        this.teamId = teamId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        rowMemberRequestBinding = RowMemberRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(rowMemberRequestBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val currentItem = list.getOrNull(position) ?: return
        rowMemberRequestBinding.tvName.text = currentItem.name ?: currentItem.toString()
        val user = UserProfileDbHandler(context).userModel

        team = try {
            mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            try {
                mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                return
            }
        }

        val isUserRequested = team.requested(user?.id, mRealm)
        with(rowMemberRequestBinding) {
            btnAccept.visibility = if (isUserRequested) View.GONE else View.VISIBLE
            btnReject.visibility = if (isUserRequested) View.GONE else View.VISIBLE

            val isEnabled = isTeamLeader && !isUserRequested
            btnAccept.isEnabled = isEnabled
            btnReject.isEnabled = isEnabled

            if (isTeamLeader) {
                btnAccept.setOnClickListener { handleClick(holder, true) }
                btnReject.setOnClickListener { handleClick(holder, false) }
            }
        }
    }

    private fun handleClick(holder: RecyclerView.ViewHolder, isAccepted: Boolean) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < list.size) {
            acceptReject(list[adapterPosition], isAccepted, adapterPosition)
        }
    }

    private fun acceptReject(userModel: RealmUserModel, isAccept: Boolean, position: Int) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val team = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            .equalTo("userId", userModel.id).findFirst()
        if (team != null) {
            if (isAccept) {
                team.docType = "membership"
                team.updated = true
            } else {
                team.deleteFromRealm()
            }
        }
        mRealm.commitTransaction()

        list.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, list.size)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderUser(rowMemberRequestBinding: RowMemberRequestBinding) : RecyclerView.ViewHolder(rowMemberRequestBinding.root)
}
