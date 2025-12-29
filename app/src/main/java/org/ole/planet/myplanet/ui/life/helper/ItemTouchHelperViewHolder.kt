package org.ole.planet.myplanet.ui.life.helper

import androidx.recyclerview.widget.RecyclerView

interface ItemTouchHelperViewHolder {
    fun onItemSelected()
    fun onItemClear(viewHolder: RecyclerView.ViewHolder?)
}
