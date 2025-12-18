package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag

interface OnCourseItemSelected {
    fun onSelectedListChange(list: List<String>)
    fun onTagClicked(tag: RealmTag?)
}
