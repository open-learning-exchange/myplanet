package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.Tag

interface OnCourseItemSelectedListener {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: MutableList<Course>)
    fun onTagClicked(tag: Tag)
}
