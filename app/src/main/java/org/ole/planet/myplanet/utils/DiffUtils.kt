package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.DiffUtil as RecyclerDiffUtil

object DiffUtils {
    /**
     * Creates a DiffUtil.ItemCallback for use with ListAdapter.
     *
     * Recommended pattern:
     * companion object {
     *     val DIFF_CALLBACK = DiffUtils.itemCallback<MyType>(
     *         areItemsTheSame = { old, new -> ... },
     *         areContentsTheSame = { old, new -> ... }
     *     )
     * }
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> itemCallback(
        areItemsTheSame: (oldItem: T, newItem: T) -> Boolean,
        areContentsTheSame: (oldItem: T, newItem: T) -> Boolean,
        getChangePayload: ((oldItem: T, newItem: T) -> Any?)? = null
    ): RecyclerDiffUtil.ItemCallback<T> {
        return object : NullableItemCallback<T>() {
            override fun areItemsTheSameNullable(oldItem: T?, newItem: T?): Boolean {
                return areItemsTheSame(oldItem as T, newItem as T)
            }

            override fun areContentsTheSameNullable(oldItem: T?, newItem: T?): Boolean {
                return areContentsTheSame(oldItem as T, newItem as T)
            }

            override fun getChangePayloadNullable(oldItem: T?, newItem: T?): Any? {
                return getChangePayload?.invoke(oldItem as T, newItem as T)
            }
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
