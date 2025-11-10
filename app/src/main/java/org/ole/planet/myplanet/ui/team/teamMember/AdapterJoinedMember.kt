package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private var isLoggedInUserTeamLeader: Boolean,
    private val actionListener: MemberActionListener
) : ListAdapter<JoinedMemberData, AdapterJoinedMember.ViewHolderUser>(JoinedMemberDataCallback) {

    companion object {
        private val JoinedMemberDataCallback =
            DiffUtils.itemCallback<JoinedMemberData>(
                areItemsTheSame = { oldItem, newItem -> oldItem.user.id == newItem.user.id },
                areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
            )
    }

    interface MemberActionListener {
        fun onRemoveMember(member: JoinedMemberData, position: Int)
        fun onMakeLeader(member: JoinedMemberData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding = RowJoinedUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(binding)
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
        binding.tvLastVisit.text = context.getString(
            R.string.last_visit,
            memberData.lastVisitDate
        )
        Glide.with(binding.memberImage.context)
            .load(member.userImage)
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
        if (isLoggedInUserTeamLeader && currentList.size > 1) {
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
                        0 -> actionListener.onRemoveMember(getItem(position), position)
                        1 -> actionListener.onMakeLeader(getItem(position))
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
            }
        } else {
            binding.icMore.visibility = View.GONE
        }
    }

    fun updateData(newList: List<JoinedMemberData>, isLoggedInUserTeamLeader: Boolean) {
        this.isLoggedInUserTeamLeader = isLoggedInUserTeamLeader
        submitList(newList.sortedByDescending { it.isLeader })
    }

    fun removeMember(memberId: String) {
        val newList = currentList.filterNot { it.user.id == memberId }
        submitList(newList)
    }

    fun updateLeadership(loggedInUserId: String?, newLeaderId: String) {
        val newList = currentList.map {
            it.copy(isLeader = it.user.id == newLeaderId)
        }.sortedByDescending { it.isLeader }

        isLoggedInUserTeamLeader = (loggedInUserId == newLeaderId)
        submitList(newList)
    }

    class ViewHolderUser(val binding: RowJoinedUserBinding) :
        RecyclerView.ViewHolder(binding.root)
}

