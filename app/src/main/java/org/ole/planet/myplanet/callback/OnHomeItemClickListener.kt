package org.ole.planet.myplanet.callback

import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam

interface OnHomeItemClickListener {
    fun openCallFragment(f: Fragment?)
    fun openLibraryDetailFragment(library: RealmMyLibrary?)
    fun showRatingDialog(resource: String?, resource_id: String?, title: String?, listener: OnRatingChangeListener?)

    fun sendSurvey(current: RealmStepExam?)
}
