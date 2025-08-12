package org.ole.planet.myplanet.ui.news

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmNews

class RealmNewsDiffCallback : DiffUtil.ItemCallback<RealmNews?>() {
    override fun areItemsTheSame(oldItem: RealmNews?, newItem: RealmNews?): Boolean {
        return oldItem?.id == newItem?.id
    }

    override fun areContentsTheSame(oldItem: RealmNews?, newItem: RealmNews?): Boolean {
        return oldItem == newItem
    }
}
