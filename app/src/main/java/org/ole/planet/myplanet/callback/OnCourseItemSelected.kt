package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag

interface OnCourseItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: List<RealmMyCourse>)
    fun onTagClicked(tag: RealmTag)
}
