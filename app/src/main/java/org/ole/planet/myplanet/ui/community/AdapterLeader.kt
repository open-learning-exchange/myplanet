package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterLeader: ListAdapter<RealmUserModel, AdapterLeader.ViewHolderLeader>(UserComparator) {
    private object UserComparator: DiffUtil.ItemCallback<RealmUserModel>() {
        override fun areItemsTheSame(oldItem: RealmUserModel, newItem: RealmUserModel): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: RealmUserModel,
            newItem: RealmUserModel
        ): Boolean = oldItem == newItem
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderLeader {
        return ViewHolderLeader(
            RowJoinedUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolderLeader, position: Int) {
        holder.bindUser(getItem(position))
    }


    class ViewHolderLeader(
        private val binding: RowJoinedUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bindUser(user: RealmUserModel) {
            with(binding) {
                tvTitle.text = user.toString()
                tvDescription.text = user.email
            }
        }
    }

}
