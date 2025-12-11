package org.ole.planet.myplanet.ui.dashboard

import org.ole.planet.myplanet.R
import javax.inject.Inject

class MyLifeProvider @Inject constructor() {
    fun getMyLifeListBase(userId: String?): List<MyLife> {
        return listOf(
            MyLife(
                "ic_myhealth",
                R.drawable.ic_myhealth,
                userId,
                "MyHealth"
            ),
            MyLife(
                "ic_submissions",
                R.drawable.ic_submissions,
                userId,
                "Submissions"
            ),
            MyLife(
                "ic_calendar",
                R.drawable.ic_calendar,
                userId,
                "Calendar"
            ),
            MyLife(
                "ic_feedback",
                R.drawable.ic_feedback,
                userId,
                "Feedback"
            )
        )
    }
}
