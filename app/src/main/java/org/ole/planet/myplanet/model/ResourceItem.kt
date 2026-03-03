package org.ole.planet.myplanet.model

data class ResourceItem(
    val id: String?,
    val title: String?,
    val description: String?,
    val createdDate: Long,
    val averageRating: String?,
    val timesRated: Int,
    val resourceId: String?,
    val isOffline: Boolean,
    val _rev: String?,
    val uploadDate: String?,
    val filename: String?,
    val subject: List<String>? = null,
    val level: List<String>? = null,
    val language: String? = null,
    val mediaType: String? = null
)
