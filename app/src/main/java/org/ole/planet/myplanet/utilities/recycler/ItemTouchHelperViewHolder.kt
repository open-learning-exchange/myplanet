package org.ole.planet.myplanet.utilities.recycler

import androidx.recyclerview.widget.RecyclerView

interface ItemTouchHelperViewHolder {
    fun onItemSelected()
    fun onItemClear(viewHolder: RecyclerView.ViewHolder?)
}
