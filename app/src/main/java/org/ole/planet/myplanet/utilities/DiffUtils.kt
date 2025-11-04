package org.ole.planet.myplanet.utilities

import androidx.recyclerview.widget.DiffUtil as RecyclerDiffUtil
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberData

object DiffUtils {
    val JOINED_MEMBER_DIFF = itemCallback<JoinedMemberData>(
        areItemsTheSame = { old, new -> old.user.id == new.user.id },
        areContentsTheSame = { old, new -> old == new }
    )

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
        areContentsTheSame: (oldItem: T, newItem: T) -> Boolean
    ): RecyclerDiffUtil.DiffResult {
        val callback = object : RecyclerDiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        }
        return RecyclerDiffUtil.calculateDiff(callback)
    }
}

