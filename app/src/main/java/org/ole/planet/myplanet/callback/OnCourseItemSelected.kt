package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.CourseItem

interface OnCourseItemSelected {
    fun onSelectedListChange(list: List<CourseItem>)
    fun onTagClicked(tag: RealmTag?)
}
