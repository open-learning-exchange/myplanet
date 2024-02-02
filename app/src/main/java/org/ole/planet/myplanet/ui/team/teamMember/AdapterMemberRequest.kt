package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterMemberRequest(private val context: Context, private val list: MutableList<RealmUserModel>, private val mRealm: Realm) : RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser>() {
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
        if (list[position].toString() == " ") {
            rowMemberRequestBinding.tvName.text = list[position].name
        } else {
            rowMemberRequestBinding.tvName.text = list[position].toString()
        }
        rowMemberRequestBinding.btnAccept.setOnClickListener {
            acceptReject(list[position], true, position)
        }
        rowMemberRequestBinding.btnReject.setOnClickListener {
            acceptReject(list[position], false, position)
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