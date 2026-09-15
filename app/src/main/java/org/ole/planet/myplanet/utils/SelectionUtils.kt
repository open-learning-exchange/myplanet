package org.ole.planet.myplanet.utils

object SelectionUtils {
    fun <T> handleCheck(b: Boolean, i: Int, selectedItems: MutableList<T?>, list: List<T?>) {
        val item = list[i]
        if (b) {
            selectedItems.add(item)
        } else {
            selectedItems.remove(item)
        }
    }
}
