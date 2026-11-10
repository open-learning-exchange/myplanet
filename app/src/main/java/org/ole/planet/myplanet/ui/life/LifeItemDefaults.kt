package org.ole.planet.myplanet.ui.life

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLife

object LifeItemDefaults {
    val knownDrawables: Map<String, Int> = mapOf(
        "ic_myhealth" to R.drawable.ic_myhealth,
        "my_achievement" to R.drawable.my_achievement,
        "ic_submissions" to R.drawable.ic_submissions,
        "ic_my_survey" to R.drawable.ic_my_survey,
        "ic_references" to R.drawable.ic_references,
        "ic_calendar" to R.drawable.ic_calendar,
        "ic_mypersonals" to R.drawable.ic_mypersonals
    )

    private val itemPairs = listOf(
        "ic_myhealth" to R.string.myhealth,
        "my_achievement" to R.string.achievements,
        "ic_submissions" to R.string.submission,
        "ic_my_survey" to R.string.my_survey,
        "ic_references" to R.string.references,
        "ic_calendar" to R.string.calendar,
        "ic_mypersonals" to R.string.mypersonals
    )

    fun forUser(userId: String?, resolveLabel: (Int) -> String): List<MyLife> =
        itemPairs.map { (imageId, stringRes) ->
            MyLife(imageId, userId, resolveLabel(stringRes))
        }
}
