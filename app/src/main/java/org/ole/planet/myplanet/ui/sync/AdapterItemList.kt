package org.ole.planet.myplanet.ui.sync

interface AdapterItemList<T> {
    fun getList(): List<T>
    fun getOldList(): List<T>
}
