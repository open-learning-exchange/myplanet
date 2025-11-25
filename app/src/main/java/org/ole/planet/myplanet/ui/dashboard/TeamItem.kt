package org.ole.planet.myplanet.ui.dashboard

import org.ole.planet.myplanet.model.RealmMyTeam

data class TeamItem(
    val team: RealmMyTeam,
    val hasUnreadMessages: Boolean,
    val hasUpcomingTasks: Boolean,
)
