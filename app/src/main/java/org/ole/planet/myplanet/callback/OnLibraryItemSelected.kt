package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.Library

interface OnLibraryItemSelected {
    fun onSelectedListChange(list: MutableList<Library?>)
    fun onTagClicked(tag: org.ole.planet.myplanet.model.RealmTag)
}
