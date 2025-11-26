package org.ole.planet.myplanet.utilities

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmMyTeam

class TeamDiffCallback : DiffUtil.ItemCallback<RealmMyTeam>() {
    override fun areItemsTheSame(oldItem: RealmMyTeam, newItem: RealmMyTeam): Boolean {
        return oldItem._id == newItem._id
    }

    override fun areContentsTheSame(oldItem: RealmMyTeam, newItem: RealmMyTeam): Boolean {
        return oldItem == newItem
    }
}
