package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmUserModel

interface OnMemberSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: MutableList<RealmUserModel?>)
}
