package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterMemberRequest(private val context: Context, private val list: MutableList<RealmUserModel>, private val mRealm: Realm, private val isTeamLeader: Boolean) : RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser>() {
    private lateinit var rowMemberRequestBinding: RowMemberRequestBinding
    private var teamId: String? = null

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

        if (isTeamLeader) {
            rowMemberRequestBinding.btnAccept.isEnabled = true
            rowMemberRequestBinding.btnReject.isEnabled = true

            rowMemberRequestBinding.btnAccept.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < list.size) {
                    acceptReject(list[adapterPosition], true, adapterPosition)
                }
            }

            rowMemberRequestBinding.btnReject.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < list.size) {
                    acceptReject(list[adapterPosition], false, adapterPosition)
                }
            }
        } else {
            rowMemberRequestBinding.btnAccept.isEnabled = false
            rowMemberRequestBinding.btnReject.isEnabled = false
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
