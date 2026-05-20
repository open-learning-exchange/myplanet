package org.ole.planet.myplanet.base

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    companion object {
        var isSurvey: Boolean = false
    }
}
