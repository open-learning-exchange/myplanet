package org.ole.planet.myplanet.callback

import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.StepExam

interface OnHomeItemClickListener {
    fun openMyFragment(f: Fragment)
    fun openCallFragment(f: Fragment)
    fun openLibraryDetailFragment(library: MyLibrary?)
    fun showRatingDialog(type: String?, resourceId: String?, title: String?, listener: OnRatingChangeListener?)

    fun sendSurvey(current: StepExam?)
}
