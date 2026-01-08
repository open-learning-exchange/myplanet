package org.ole.planet.myplanet.ui.teams.members

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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.repository.JoinedMemberData
import org.ole.planet.myplanet.utilities.NavigationHelper

class TeamMembersAdapter(
    private val context: Context,
    private val currentUserId: String?,
    private val actionListener: MemberActionListener
) : ListAdapter<JoinedMemberData, TeamMembersAdapter.ViewHolderUser>(DIFF_CALLBACK) {
    private var isLoggedInUserTeamLeader: Boolean = false
    interface MemberActionListener {
        fun onRemoveMember(member: JoinedMemberData, position: Int)
        fun onMakeLeader(member: JoinedMemberData)
        fun onLeaveTeam()
    }
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<JoinedMemberData>() {
            override fun areItemsTheSame(oldItem: JoinedMemberData, newItem: JoinedMemberData): Boolean {
                return oldItem.user.id == newItem.user.id
            }

            override fun areContentsTheSame(oldItem: JoinedMemberData, newItem: JoinedMemberData): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: JoinedMemberData, newItem: JoinedMemberData): Any? {
                val payload = Bundle()
                if (oldItem.isLeader != newItem.isLeader) {
                    payload.putBoolean("KEY_LEADER", newItem.isLeader)
                }
                return if (payload.isEmpty) null else payload
            }
        }
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
        val memberData = getItem(position)
        val member = memberData.user
        val binding = holder.binding

        binding.tvTitle.text = if (member.toString() == " ") member.name else member.toString()
        binding.tvDescription.text = context.getString(
            R.string.member_description,
            member.getRoleAsString(),
            memberData.visitCount
        )
        val lastVisitDate = if (memberData.lastVisitDate != null) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(memberData.lastVisitDate))
        } else {
            context.getString(R.string.no_visit)
        }
        binding.tvLastVisit.text = context.getString(
            R.string.last_visit,
            lastVisitDate
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
                "${memberData.visitCount}",
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

    private fun checkUserAndShowOverflowMenu(binding: RowJoinedUserBinding, position: Int) {
        if (isLoggedInUserTeamLeader && itemCount > 1) {
            binding.icMore.visibility = View.VISIBLE
            binding.icMore.setOnClickListener {
                val currentMember = getItem(position)
                val isOwnCard = currentMember.user.id == currentUserId

                val overflowMenuOptions = if (isOwnCard) {
                    arrayOf(context.getString(R.string.leave))
                } else {
                    arrayOf(
                        context.getString(R.string.remove),
                        context.getString(R.string.make_leader)
                    )
                }

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
                    if (isOwnCard) {
                        when (i) {
                            0 -> actionListener.onLeaveTeam()
                        }
                    } else {
                        when (i) {
                            0 -> actionListener.onRemoveMember(getItem(position), position)
                            1 -> actionListener.onMakeLeader(getItem(position))
                        }
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
            }
        } else {
            binding.icMore.visibility = View.GONE
        }
    }

    fun updateData(newList: List<JoinedMemberData>, isLoggedInUserTeamLeader: Boolean) {
        this.isLoggedInUserTeamLeader = isLoggedInUserTeamLeader
        submitList(newList)
    }

    class ViewHolderUser(val binding: RowJoinedUserBinding) :
        RecyclerView.ViewHolder(binding.root)
}
