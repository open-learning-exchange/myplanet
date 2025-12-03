package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag

data class LibraryItem(
    val id: String,
    val resourceId: String?,
    val originalObject: RealmMyLibrary,
    val title: String?,
    val description: String?,
    val createdDate: Long,
    val createdDateString: String?,
    val timesRated: String?,
    val averageRating: String?,
    val rating: Float,
    val isResourceOffline: Boolean,
    val tags: List<RealmTag>,
    val downloadedIconVisibility: Int, // View.VISIBLE or View.INVISIBLE
    val downloadedIconContentDescription: String?,
    val checkboxVisible: Int, // View.VISIBLE or View.GONE
    val checkboxChecked: Boolean,
    val checkboxContentDescription: String?
)
