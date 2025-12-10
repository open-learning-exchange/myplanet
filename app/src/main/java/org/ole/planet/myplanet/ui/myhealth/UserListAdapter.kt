package org.ole.planet.myplanet.ui.myhealth

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListAdapter(private val context: Context, private val onUserSelected: (RealmUserModel) -> Unit) :
    ListAdapter<RealmUserModel, UserListAdapter.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val um = getItem(position)
        holder.binding.txtName.text = context.getString(R.string.two_strings, um.getFullName(), "(${um.name})")
        holder.binding.txtJoined.text = context.getString(R.string.joined_colon, TimeUtils.formatDate(um.joinDate))

        if (!TextUtils.isEmpty(um.userImage)) {
            Glide.with(holder.binding.ivUser.context)
                .load(um.userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(holder.binding.ivUser)
        } else {
            holder.binding.ivUser.setImageResource(R.drawable.profile)
        }

        holder.itemView.setOnClickListener {
            onUserSelected(um)
        }
    }

    class ViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val diffCallback = DiffUtils.itemCallback<RealmUserModel>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
