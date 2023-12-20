package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyLife

interface OnMyLifeItemSelected {
    fun onSelectedListChange(list: List<RealmMyLife?>?)
}
