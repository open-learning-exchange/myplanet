package org.ole.planet.myplanet.model.dto

data class LibraryItem(
    val id: String?,
    val _id: String?,
    val _rev: String?,
    val title: String?,
    val description: String?,
    val timesRated: Int,
    val averageRating: String?,
    val createdDate: Long,
    val uploadDate: String?,
    val resourceOffline: Boolean,
    val resourceId: String?,
    val resourceLocalAddress: String?,
    val filename: String?,
    val mediaType: String?,
    val language: String?,
    val subject: List<String>?,
    val level: List<String>?,
    var tags: List<TagItem> = emptyList(),
    val userId: List<String>?
)
