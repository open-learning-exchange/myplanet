package org.ole.planet.myplanet.utilities.diff

import androidx.recyclerview.widget.DiffUtil

class RealmDiffCallback<T>(
    private val oldList: List<T>,
    private val newList: List<T>,
    private val idSelector: (T) -> String?
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldId = idSelector(oldList[oldItemPosition])
        val newId = idSelector(newList[newItemPosition])
        return oldId != null && oldId == newId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

