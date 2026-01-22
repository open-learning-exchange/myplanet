package org.ole.planet.myplanet.callback

import androidx.recyclerview.widget.RecyclerView

interface OnItemDragStateListener {
    fun onItemSelected()
    fun onItemClear(viewHolder: RecyclerView.ViewHolder?)
}
