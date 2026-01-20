package org.ole.planet.myplanet.callback

interface OnItemMoveListener {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
}
