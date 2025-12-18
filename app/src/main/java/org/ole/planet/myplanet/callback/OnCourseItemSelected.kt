package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.ui.courses.CourseItem

interface OnCourseItemSelected {
    fun onSelectedListChange(list: MutableList<CourseItem?>)
    fun onTagClicked(tag: RealmTag?)
}
