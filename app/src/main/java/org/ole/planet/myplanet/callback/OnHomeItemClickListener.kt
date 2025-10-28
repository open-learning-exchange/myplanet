package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.ui.navigation.DashboardDestination

interface OnHomeItemClickListener {
    fun openMyFragment(destination: DashboardDestination)
    fun openCallFragment(destination: DashboardDestination)
    fun openLibraryDetailFragment(library: RealmMyLibrary?)
    fun showRatingDialog(type: String?, resourceId: String?, title: String?, listener: OnRatingChangeListener?)

    fun sendSurvey(current: RealmStepExam?)
}
