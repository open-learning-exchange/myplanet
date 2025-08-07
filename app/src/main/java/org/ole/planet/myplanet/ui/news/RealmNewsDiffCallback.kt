package org.ole.planet.myplanet.ui.news

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmNews

class RealmNewsDiffCallback(
    private val oldList: List<RealmNews?>,
    private val newList: List<RealmNews?>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        return oldItem?.id == newItem?.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        return oldItem == newItem
    }
}
