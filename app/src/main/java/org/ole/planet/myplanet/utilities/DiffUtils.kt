package org.ole.planet.myplanet.utilities

import androidx.recyclerview.widget.DiffUtil as RecyclerDiffUtil

object DiffUtils {
    fun <T : Any> itemCallback(
        areItemsTheSame: (oldItem: T, newItem: T) -> Boolean,
        areContentsTheSame: (oldItem: T, newItem: T) -> Boolean
    ): RecyclerDiffUtil.ItemCallback<T> {
        return object : RecyclerDiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = areItemsTheSame(oldItem, newItem)
            override fun areContentsTheSame(oldItem: T, newItem: T) = areContentsTheSame(oldItem, newItem)
        }
    }

    fun <T> calculateDiff(
        oldList: List<T>,
        newList: List<T>,
        areItemsTheSame: (oldItem: T, newItem: T) -> Boolean,
        areContentsTheSame: (oldItem: T, newItem: T) -> Boolean,
        getChangePayload: ((oldItem: T, newItem: T) -> Any?)? = null
    ): RecyclerDiffUtil.DiffResult {
        val callback = object : RecyclerDiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                return getChangePayload?.invoke(oldList[oldItemPosition], newList[newItemPosition])
            }
        }
        return RecyclerDiffUtil.calculateDiff(callback)
    }
}
