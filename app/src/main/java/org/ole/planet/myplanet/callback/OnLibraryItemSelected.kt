package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag

interface OnLibraryItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: List<String>)
    @JvmSuppressWildcards
    fun onTagClicked(realmTag: RealmTag)
}
