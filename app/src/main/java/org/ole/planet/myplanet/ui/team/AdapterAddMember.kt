package org.ole.planet.myplanet.ui.team

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowAddMemberBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities

class AdapterAddMember(private val list: List<RealmUserModel>): RecyclerView.Adapter<AdapterAddMember.ViewHolderAddMember>()  {
    private lateinit var rowAddMemberBinding: RowAddMemberBinding
    private val selectedMembers: MutableList<RealmUserModel?> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderAddMember {
        rowAddMemberBinding = RowAddMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderAddMember(rowAddMemberBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderAddMember, position: Int) {
        println("hi")
        val member = list[position]
        rowAddMemberBinding.tvName.text = member.name
        rowAddMemberBinding.tvLastVisit.text = member.email
        rowAddMemberBinding.checkbox.setOnClickListener { view: View ->
            Utilities.handleCheck((view as CheckBox).isChecked, position, selectedMembers, list)
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderAddMember(rowAddMemberBinding: RowAddMemberBinding) : RecyclerView.ViewHolder(rowAddMemberBinding.root)

}