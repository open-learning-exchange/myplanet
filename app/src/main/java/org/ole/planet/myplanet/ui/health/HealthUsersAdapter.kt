package org.ole.planet.myplanet.ui.health

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.TimeUtils

class HealthUsersAdapter(private val clickListener: ((UserEntity) -> Unit)? = null) :
    ListAdapter<UserEntity, HealthUsersAdapter.ViewHolder>(
        DiffUtils.itemCallback<UserEntity>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new ->
                old.name == new.name &&
                old.userImage == new.userImage &&
                old.joinDate == new.joinDate
            },
            getChangePayload = { old, new ->
                val diffs = mutableListOf<String>()
                if (old.name != new.name) diffs.add("name")
                if (old.userImage != new.userImage) diffs.add("userImage")
                if (old.joinDate != new.joinDate) diffs.add("joinDate")
                if (diffs.isEmpty()) null else diffs
            }
        )
    ) {

    class ViewHolder(private val binding: ItemUserBinding, private val avatarSize: Int) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: UserEntity, clickListener: ((UserEntity) -> Unit)?) {
            bindName(user)
            bindDate(user)
            bindImage(user)
            binding.root.setOnClickListener {
                clickListener?.invoke(user)
            }
        }

        fun bindName(user: UserEntity) {
            binding.txtName.text = binding.root.context.getString(R.string.two_strings, user.getFullName(), "(${user.name})")
        }

        fun bindDate(user: UserEntity) {
            binding.txtJoined.text = binding.root.context.getString(R.string.joined_colon, TimeUtils.formatDate(user.joinDate))
        }

        fun bindImage(user: UserEntity) {
            if (!TextUtils.isEmpty(user.userImage)) {
                ImageUtils.loadProfileImage(user.userImage, binding.ivUser, avatarSize)
            } else {
                binding.ivUser.setImageResource(R.drawable.profile)
            }
        }
    }

    private var avatarSize = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (avatarSize == 0) {
            avatarSize = parent.context.resources.getDimensionPixelSize(R.dimen._80dp)
        }
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, avatarSize)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val diffs = payloads.filterIsInstance<List<*>>().flatten()
        if (diffs.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val user = getItem(position)
            if ("name" in diffs) {
                holder.bindName(user)
            }
            if ("userImage" in diffs) {
                holder.bindImage(user)
            }
            if ("joinDate" in diffs) {
                holder.bindDate(user)
            }
        }
    }
}
