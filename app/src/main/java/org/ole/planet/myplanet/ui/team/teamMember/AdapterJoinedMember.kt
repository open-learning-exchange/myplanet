package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.DiffUtils

data class JoinedMemberData(
    val user: RealmUserModel,
    val visitCount: Long,
    val lastVisitDate: String,
    val offlineVisits: String,
    val profileLastVisit: String,
    var isLeader: Boolean
)

class AdapterJoinedMember(
    private val context: Context,
    private val list: MutableList<JoinedMemberData>,
    private var isLoggedInUserTeamLeader: Boolean,
    private val actionListener: MemberActionListener
) : RecyclerView.Adapter<AdapterJoinedMember.ViewHolderUser>() {

    interface MemberActionListener {
        fun onRemoveMember(member: JoinedMemberData, position: Int)
        fun onMakeLeader(member: JoinedMemberData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding = RowJoinedUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolderUser,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0] as Bundle
            if (payload.containsKey("KEY_LEADER")) {
                val isLeader = payload.getBoolean("KEY_LEADER")
                holder.binding.tvIsLeader.visibility = if (isLeader) View.VISIBLE else View.GONE
                if (isLeader) {
                    holder.binding.tvIsLeader.text = context.getString(R.string.team_leader)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val memberData = list[position]
        val member = memberData.user
        val binding = holder.binding

        binding.tvTitle.text = if (member.toString() == " ") member.name else member.toString()
        binding.tvDescription.text = context.getString(
            R.string.member_description,
            member.getRoleAsString(),
            memberData.visitCount
        )
        binding.tvLastVisit.text = context.getString(
            R.string.last_visit,
            memberData.lastVisitDate
        )
        Glide.with(binding.memberImage.context)
            .load(member.userImage)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(binding.memberImage)

        if (memberData.isLeader) {
            binding.tvIsLeader.visibility = View.VISIBLE
            binding.tvIsLeader.text = context.getString(R.string.team_leader)
        } else {
            binding.tvIsLeader.visibility = View.GONE
        }

        checkUserAndShowOverflowMenu(binding, position)

        holder.itemView.setOnClickListener {
            val activity = it.context as AppCompatActivity
            val userName = "${member.firstName} ${member.lastName}".trim().ifBlank {
                member.name
            }
            val fragment = MemberDetailFragment.newInstance(
                userName.toString(),
                member.email.toString(),
                member.dob.toString().substringBefore("T"),
                member.language.toString(),
                member.phoneNumber.toString(),
                memberData.offlineVisits,
                memberData.profileLastVisit,
                "${member.firstName} ${member.lastName}",
                member.level.toString(),
                member.userImage
            )
            NavigationHelper.replaceFragment(
                activity.supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    private fun checkUserAndShowOverflowMenu(
        binding: RowJoinedUserBinding,
        position: Int
    ) {
        if (isLoggedInUserTeamLeader && list.size > 1) {
            binding.icMore.visibility = View.VISIBLE
            binding.icMore.setOnClickListener {
                val overflowMenuOptions = arrayOf(
                    context.getString(R.string.remove),
                    context.getString(R.string.make_leader)
                )
                val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                val adapter = object : ArrayAdapter<CharSequence>(
                    context,
                    android.R.layout.simple_list_item_1,
                    overflowMenuOptions
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val color = ContextCompat.getColor(context, R.color.daynight_textColor)
                        view.setTextColor(color)
                        return view
                    }
                }
                builder.setAdapter(adapter) { _, i ->
                    when (i) {
                        0 -> actionListener.onRemoveMember(list[position], position)
                        1 -> actionListener.onMakeLeader(list[position])
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
            }
        } else {
            binding.icMore.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<JoinedMemberData>, isLoggedInUserTeamLeader: Boolean) {
        if (this.list === newList) return
        this.isLoggedInUserTeamLeader = isLoggedInUserTeamLeader
        val oldList = ArrayList(this.list)
        val diffResult = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { old, new -> old.user.id == new.user.id },
            areContentsTheSame = { old, new -> old == new },
            getChangePayload = { old, new ->
                val payload = Bundle()
                if (old.isLeader != new.isLeader) {
                    payload.putBoolean("KEY_LEADER", new.isLeader)
                }
                if (payload.isEmpty) null else payload
            }
        )
        this.list.clear()
        this.list.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun removeMember(memberId: String) {
        val position = list.indexOfFirst { it.user.id == memberId }
        if (position != -1) {
            list.removeAt(position)
            notifyItemRemoved(position)

            if (list.isNotEmpty()) {
                notifyItemRangeChanged(0, list.size)
            }
        }
    }

    fun updateLeadership(loggedInUserId: String?, newLeaderId: String) {
        var oldLeaderPos = -1
        var newLeaderPos = -1

        list.forEachIndexed { index, memberData ->
            if (memberData.isLeader) {
                memberData.isLeader = false
                oldLeaderPos = index
            }
            if (memberData.user.id == newLeaderId) {
                memberData.isLeader = true
                newLeaderPos = index
            }
        }

        isLoggedInUserTeamLeader = (loggedInUserId == newLeaderId)

        if (newLeaderPos > 0) {
            val newLeader = list.removeAt(newLeaderPos)
            list.add(0, newLeader)
            notifyItemMoved(newLeaderPos, 0)
        }

        if (oldLeaderPos != -1) notifyItemChanged(if (oldLeaderPos == 0) 1 else oldLeaderPos)
        notifyItemChanged(0)

        if (list.size > 2) {
            notifyItemRangeChanged(1, list.size - 1)
        }
    }

    class ViewHolderUser(val binding: RowJoinedUserBinding) :
        RecyclerView.ViewHolder(binding.root)
}
