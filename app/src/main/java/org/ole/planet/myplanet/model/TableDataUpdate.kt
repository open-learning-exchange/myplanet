package org.ole.planet.myplanet.model

data class TableDataUpdate(
    val table: String,
    val newItemsCount: Int,
    val updatedItemsCount: Int,
    val shouldRefreshUI: Boolean = true
)
