package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.CourseItem

interface OnCourseItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: MutableList<CourseItem?>)
    fun onTagClicked(tag: RealmTag?)
}
