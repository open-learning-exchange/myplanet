package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmMyLibrary

data class LibraryItem(
    val id: String?,
    val _id: String?,
    val title: String?,
    val description: String?,
    val createdDate: Long?,
    val averageRating: String?,
    val timesRated: Int?,
    val isResourceOffline: Boolean,
    val resourceId: String?
)

fun RealmMyLibrary.toLibraryItem(): LibraryItem {
    return LibraryItem(
        id = this.id,
        _id = this._id,
        title = this.title,
        description = this.description,
        createdDate = this.createdDate,
        averageRating = this.averageRating,
        timesRated = this.timesRated,
        isResourceOffline = this.isResourceOffline(),
        resourceId = this.resourceId
    )
}
