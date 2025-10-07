package org.ole.planet.myplanet.ui.team

import android.graphics.PorterDuff
import android.view.View
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding

sealed class TeamActionButtonState(
    val iconRes: Int?,
    val isEnabled: Boolean,
    val contentDescription: String?,
    val visibility: Int = View.VISIBLE,
    val colorFilter: Int? = null,
    val colorFilterMode: PorterDuff.Mode = PorterDuff.Mode.SRC_IN,
) {
    object Hidden : TeamActionButtonState(iconRes = null, isEnabled = false, contentDescription = null, visibility = View.GONE)

    class Join(contentDescription: String) : TeamActionButtonState(
        iconRes = R.drawable.ic_join_request,
        isEnabled = true,
        contentDescription = contentDescription,
    )

    class Requested(contentDescription: String, colorFilter: Int) : TeamActionButtonState(
        iconRes = R.drawable.baseline_hourglass_top_24,
        isEnabled = false,
        contentDescription = contentDescription,
        colorFilter = colorFilter,
    )

    class Leave(contentDescription: String) : TeamActionButtonState(
        iconRes = R.drawable.logout,
        isEnabled = true,
        contentDescription = contentDescription,
    )

    class Edit(contentDescription: String) : TeamActionButtonState(
        iconRes = R.drawable.ic_edit,
        isEnabled = true,
        contentDescription = contentDescription,
    )
}

fun ItemTeamListBinding.applyActionButtonState(state: TeamActionButtonState) {
    joinLeave.visibility = state.visibility
    joinLeave.isEnabled = state.isEnabled
    joinLeave.contentDescription = state.contentDescription

    state.iconRes?.let { joinLeave.setImageResource(it) }

    if (state.colorFilter != null) {
        joinLeave.setColorFilter(state.colorFilter, state.colorFilterMode)
    } else {
        joinLeave.clearColorFilter()
    }
}
