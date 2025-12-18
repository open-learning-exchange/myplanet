package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.LibraryItem

interface OnLibraryItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: MutableList<LibraryItem>)
    @JvmSuppressWildcards
    fun onTagClicked(realmTag: RealmTag)
    @JvmSuppressWildcards
    fun onItemClicked(libraryId: String)
}
