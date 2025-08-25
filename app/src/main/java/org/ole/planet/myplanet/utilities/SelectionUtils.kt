package org.ole.planet.myplanet.utilities

object SelectionUtils {
    fun <T> handleCheck(b: Boolean, i: Int, selectedItems: MutableList<T?>, list: List<T?>) {
        if (b) {
            selectedItems.add(list[i])
        } else if (selectedItems.contains(list[i])) {
            selectedItems.remove(list[i])
        }
    }
}

