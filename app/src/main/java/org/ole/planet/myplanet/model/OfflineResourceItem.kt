package org.ole.planet.myplanet.model

data class OfflineResourceItem(
    val resourceId: String,
    val title: String,
    val filePaths: List<String>,
    val totalSizeBytes: Long,
    val isChecked: Boolean = false
)
