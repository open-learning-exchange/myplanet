package org.ole.planet.myplanet.ui.news

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmNews

object RealmNewsDiffCallback : DiffUtil.ItemCallback<RealmNews>() {
    private fun safeId(n: RealmNews?): String? = if (n?.isValid == true) n.id else null

    override fun areItemsTheSame(oldItem: RealmNews, newItem: RealmNews): Boolean {
        val oId = safeId(oldItem)
        val nId = safeId(newItem)
        return oId != null && oId == nId
    }

    override fun areContentsTheSame(oldItem: RealmNews, newItem: RealmNews): Boolean {
        if (oldItem.isValid != true || newItem.isValid != true) return false

        return oldItem.id == newItem.id &&
            oldItem.time == newItem.time &&
            oldItem.isEdited == newItem.isEdited &&
            oldItem.message == newItem.message
    }
}
