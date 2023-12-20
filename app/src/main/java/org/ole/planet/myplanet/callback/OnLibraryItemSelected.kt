package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag

interface OnLibraryItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: List<RealmMyLibrary>)
    @JvmSuppressWildcards
    fun onTagClicked(realmTag: RealmTag)
}

