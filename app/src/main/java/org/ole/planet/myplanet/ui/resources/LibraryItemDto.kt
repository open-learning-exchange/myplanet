package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.RealmMyLibrary

data class LibraryItemDto(
    val id: String?,
    val _id: String?,
    val resourceId: String?,
    val title: String?,
    val description: String?,
    val createdDate: Long?,
    val averageRating: String?,
    val timesRated: Int,
    val isResourceOffline: Boolean,
)

fun LibraryItemDto.toRealmMyLibrary(): RealmMyLibrary {
    val dto = this
    return RealmMyLibrary().apply {
        id = dto.id
        _id = dto._id
        resourceId = dto.resourceId
        title = dto.title
        description = dto.description
        createdDate = dto.createdDate ?: 0L
        averageRating = dto.averageRating
        timesRated = dto.timesRated
        resourceOffline = dto.isResourceOffline
    }
}
