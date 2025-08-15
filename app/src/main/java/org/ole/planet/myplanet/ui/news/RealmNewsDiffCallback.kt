package org.ole.planet.myplanet.ui.news

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmNews

class RealmNewsDiffCallback(
    private val oldList: List<RealmNews?>,
    private val newList: List<RealmNews?>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    private fun safeId(n: RealmNews?): String? = if (n?.isValid == true) n.id else null

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oId = safeId(oldList[oldItemPosition])
        val nId = safeId(newList[newItemPosition])
        return oId != null && oId == nId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val o = oldList[oldItemPosition]
        val n = newList[newItemPosition]

        if (o?.isValid != true || n?.isValid != true) return false

        return o.id == n.id &&
                o.time == n.time &&
                o.isEdited == n.isEdited &&
                o.message == n.message
    }
}
