package org.ole.planet.myplanet.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.domain.models.Leader
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterLeader: ListAdapter<Leader, AdapterLeader.ViewHolderLeader>(UserComparator) {
    private object UserComparator: DiffUtil.ItemCallback<Leader>() {
        override fun areItemsTheSame(oldItem: Leader, newItem: Leader): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: Leader,
            newItem: Leader
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
        fun bindUser(user: Leader) {
            with(binding) {
                tvTitle.text = user.name
                tvDescription.text = user.email
            }
        }
    }

}
