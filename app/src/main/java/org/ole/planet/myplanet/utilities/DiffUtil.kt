package org.ole.planet.myplanet.utilities

import androidx.recyclerview.widget.DiffUtil as AndroidDiffUtil

object DiffUtil {
    fun <T> calculateDiff(
        oldList: List<T>,
        newList: List<T>,
        areItemsTheSame: (T, T) -> Boolean,
        areContentsTheSame: (T, T) -> Boolean
    ): AndroidDiffUtil.DiffResult {
        val callback = object : AndroidDiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        }
        return AndroidDiffUtil.calculateDiff(callback)
    }
}
