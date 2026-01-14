package org.ole.planet.myplanet.base

import androidx.recyclerview.widget.RecyclerView

interface ItemDragViewHolder {
    fun onItemSelected()
    fun onItemClear(viewHolder: RecyclerView.ViewHolder?)
}
