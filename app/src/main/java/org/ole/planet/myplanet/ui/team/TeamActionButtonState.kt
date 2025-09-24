package org.ole.planet.myplanet.ui.team

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import org.ole.planet.myplanet.R

sealed class TeamActionButtonState {
    abstract val isEnabled: Boolean
    abstract val contentDescription: String?
    open val iconRes: Int? = null
    @ColorInt
    open val tintColor: Int? = null

    object Hidden : TeamActionButtonState() {
        override val isEnabled: Boolean = false
        override val contentDescription: String? = null
    }

    data class Join(
        private val description: String,
    ) : TeamActionButtonState() {
        override val isEnabled: Boolean = true
        override val contentDescription: String = description
        @DrawableRes
        override val iconRes: Int = R.drawable.ic_join_request
    }

    data class Requested(
        private val description: String,
        private val tint: Int,
    ) : TeamActionButtonState() {
        override val isEnabled: Boolean = false
        override val contentDescription: String = description
        @DrawableRes
        override val iconRes: Int = R.drawable.baseline_hourglass_top_24
        @ColorInt
        override val tintColor: Int = tint
    }

    data class Leave(
        private val description: String,
    ) : TeamActionButtonState() {
        override val isEnabled: Boolean = true
        override val contentDescription: String = description
        @DrawableRes
        override val iconRes: Int = R.drawable.logout
    }

    data class Edit(
        private val description: String,
    ) : TeamActionButtonState() {
        override val isEnabled: Boolean = true
        override val contentDescription: String = description
        @DrawableRes
        override val iconRes: Int = R.drawable.ic_edit
    }
}
