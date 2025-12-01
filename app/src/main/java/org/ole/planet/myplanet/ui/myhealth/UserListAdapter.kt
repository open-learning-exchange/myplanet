package org.ole.planet.myplanet.ui.myhealth

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListAdapter : ListAdapter<RealmUserModel, UserListAdapter.ViewHolder>(USER_COMPARATOR) {
    var onItemClickListener: ((RealmUserModel) -> Unit)? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val um = getItem(position)
        holder.bind(um)
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(um)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.txt_name)
        private val joined: TextView = itemView.findViewById(R.id.txt_joined)
        private val image: ImageView = itemView.findViewById(R.id.iv_user)

        fun bind(um: RealmUserModel) {
            tvName.text = itemView.context.getString(R.string.two_strings, um.getFullName(), "(${um.name})")
            joined.text = itemView.context.getString(R.string.joined_colon, TimeUtils.formatDate(um.joinDate))

            if (!TextUtils.isEmpty(um.userImage)) {
                Glide.with(image.context)
                    .load(um.userImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(image)
            } else {
                image.setImageResource(R.drawable.profile)
            }
        }
    }

    companion object {
        private val USER_COMPARATOR = object : DiffUtil.ItemCallback<RealmUserModel>() {
            override fun areItemsTheSame(oldItem: RealmUserModel, newItem: RealmUserModel): Boolean {
                return oldItem._id == newItem._id
            }

            override fun areContentsTheSame(oldItem: RealmUserModel, newItem: RealmUserModel): Boolean {
                return oldItem.name == newItem.name && oldItem.userImage == newItem.userImage
            }
        }
    }
}
