package org.ole.planet.myplanet.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.ole.planet.myplanet.R

enum class FeedbackStatus(
    val persistedValue: String,
    @param:StringRes @field:StringRes private val displayTextRes: Int,
    @param:DrawableRes @field:DrawableRes val backgroundRes: Int,
) {
    OPEN("Open", R.string.feedback_status_open, R.drawable.bg_primary),
    CLOSED("Closed", R.string.feedback_status_closed, R.drawable.bg_grey);

    fun displayText(context: Context): String = context.getString(displayTextRes)

    val isClosed: Boolean
        get() = this == CLOSED

    companion object {
        fun fromPersistedValue(value: String?): FeedbackStatus {
            return values().firstOrNull { it.persistedValue.equals(value, ignoreCase = true) }
                ?: OPEN
        }
    }
}
