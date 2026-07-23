package org.ole.planet.myplanet.ui.user

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.TimeUtils

class UserArrayAdapter(
    private val onItemClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, UserArrayAdapter.ViewHolder>(
    DiffUtils.itemCallback<UserEntity>(
        { oldItem, newItem -> oldItem.id == newItem.id },
        { oldItem, newItem -> oldItem.id == newItem.id && oldItem.name == newItem.name }
    )
) {

    var selectedUser: UserEntity? = null
    private var avatarSize = 0

    class ViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (avatarSize == 0) {
            avatarSize = parent.context.resources.getDimensionPixelSize(R.dimen._80dp)
        }
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            val user = getItem(position)
            val context = holder.itemView.context
            if (user.id == selectedUser?.id) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_300))
            } else {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context

        holder.binding.txtName.text = context.getString(R.string.two_strings, user.getFullName(), "(${user.name})")
        holder.binding.txtJoined.text = context.getString(R.string.joined_colon, TimeUtils.formatDate(user.joinDate))

        if (!TextUtils.isEmpty(user.userImage)) {
            ImageUtils.loadProfileImage(user.userImage, holder.binding.ivUser, avatarSize)
        } else {
            holder.binding.ivUser.setImageResource(R.drawable.profile)
        }

        if (user.id == selectedUser?.id) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_300))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        }

        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener
            val previousUser = selectedUser
            selectedUser = user
            val prevPos = currentList.indexOfFirst { it.id == previousUser?.id }
            if (prevPos != -1) notifyItemChanged(prevPos, SELECTION_PAYLOAD)
            notifyItemChanged(currentPos, SELECTION_PAYLOAD)
            onItemClick(user)
        }
    }

    companion object {
        private const val SELECTION_PAYLOAD = "selection_payload"
    }
}
