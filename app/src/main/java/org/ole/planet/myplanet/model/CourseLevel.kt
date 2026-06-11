package org.ole.planet.myplanet.model

import androidx.annotation.StringRes
import org.ole.planet.myplanet.R

enum class CourseLevel(val serverValue: String, @StringRes val displayRes: Int) {
    BEGINNER("Beginner", R.string.level_beginner),
    INTERMEDIATE("Intermediate", R.string.level_intermediate),
    ADVANCED("Advanced", R.string.level_advanced),
    EXPERT("Expert", R.string.level_expert);

    companion object {
        fun fromServerValue(value: String?) =
            entries.firstOrNull { it.serverValue.equals(value, ignoreCase = true) }
    }
}
