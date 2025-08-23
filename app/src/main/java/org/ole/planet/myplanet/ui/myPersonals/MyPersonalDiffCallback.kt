package org.ole.planet.myplanet.ui.myPersonals

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalDiffCallback : DiffUtil.ItemCallback<RealmMyPersonal>() {
    override fun areItemsTheSame(oldItem: RealmMyPersonal, newItem: RealmMyPersonal): Boolean {
        return oldItem._id == newItem._id
    }

    override fun areContentsTheSame(oldItem: RealmMyPersonal, newItem: RealmMyPersonal): Boolean {
        return oldItem.title == newItem.title &&
                oldItem.description == newItem.description &&
                oldItem.date == newItem.date
    }
}
