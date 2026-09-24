package org.ole.planet.myplanet.ui.life

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLife

object LifeDefaults {
    private val defaultItemPairs = listOf(
        "ic_myhealth" to R.string.myhealth,
        "my_achievement" to R.string.achievements,
        "ic_submissions" to R.string.submission,
        "ic_my_survey" to R.string.my_survey,
        "ic_references" to R.string.references,
        "ic_calendar" to R.string.calendar,
        "ic_mypersonals" to R.string.mypersonals
    )

    fun defaultItems(userId: String?, resolveLabel: (Int) -> String): List<MyLife> =
        defaultItemPairs.map { (imageId, stringRes) ->
            MyLife(imageId, userId, resolveLabel(stringRes))
        }
}
