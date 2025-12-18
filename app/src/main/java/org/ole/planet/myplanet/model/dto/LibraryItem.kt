package org.ole.planet.myplanet.model.dto

data class LibraryItem(
    val id: String?,
    val title: String?,
    val description: String?,
    val timesRated: Int,
    val averageRating: String?,
    val createdDate: Long?,
    val isResourceOffline: Boolean,
    val resourceId: String?,
)
