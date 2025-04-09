package org.ole.planet.myplanet.ui.team

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnMemberSelected
import org.ole.planet.myplanet.databinding.RowAddMemberBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities

class AdapterAddMember(private var list: List<RealmUserModel>): RecyclerView.Adapter<AdapterAddMember.ViewHolderAddMember>()  {
    private lateinit var rowAddMemberBinding: RowAddMemberBinding
    private val selectedMembers: MutableList<RealmUserModel?> = ArrayList()
    private var listener: OnMemberSelected? = null

    fun setListener(listener: OnMemberSelected?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderAddMember {
        rowAddMemberBinding = RowAddMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderAddMember(rowAddMemberBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderAddMember, position: Int) {
        val member = list[position]
        println(member.name)
        holder.binding.tvName.text = member.name

        Glide.with(context)
            .load(member.userImage)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(holder.binding.memberImage)

        if(member.rolesList.isNullOrEmpty()){
            holder.binding.tvLastVisit.text = "Learner"
        } else {
            holder.binding.tvLastVisit.text = member.rolesList?.get(0)
        }
        holder.binding.checkbox.setOnClickListener { view: View ->
            Utilities.handleCheck((view as CheckBox).isChecked, position, selectedMembers, list)
            listener?.onSelectedListChange(selectedMembers)
        }
    }

    fun setMemberList(memberList: List<RealmUserModel>) {
        this.list = memberList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderAddMember(val binding: RowAddMemberBinding) : RecyclerView.ViewHolder(binding.root)}