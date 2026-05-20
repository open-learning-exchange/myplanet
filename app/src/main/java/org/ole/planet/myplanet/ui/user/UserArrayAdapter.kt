package org.ole.planet.myplanet.ui.user

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils

class UserArrayAdapter(
    private val onItemClick: (RealmUser) -> Unit
) : ListAdapter<RealmUser, UserArrayAdapter.ViewHolder>(
    DiffUtils.itemCallback<RealmUser>(
        { oldItem, newItem -> oldItem.id == newItem.id },
        { oldItem, newItem -> oldItem.id == newItem.id && oldItem.name == newItem.name }
    )
) {

    var selectedPosition = 0

    class ViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        val context = holder.itemView.context

        holder.binding.txtName.text = context.getString(R.string.two_strings, user.getFullName(), "(${user.name})")
        holder.binding.txtJoined.text = context.getString(R.string.joined_colon, TimeUtils.formatDate(user.joinDate))

        if (!TextUtils.isEmpty(user.userImage)) {
            Glide.with(context)
                .load(user.userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(holder.binding.ivUser)
        } else {
            holder.binding.ivUser.setImageResource(R.drawable.profile)
        }

        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_300))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        }

        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener
            val oldPos = selectedPosition
            selectedPosition = currentPos
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onItemClick(user)
        }
    }
}
