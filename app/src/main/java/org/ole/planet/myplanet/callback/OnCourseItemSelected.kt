package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.ui.courses.CourseItemDto

interface OnCourseItemSelected {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: MutableList<CourseItemDto>)
    fun onTagClicked(tag: RealmTag?)
}
