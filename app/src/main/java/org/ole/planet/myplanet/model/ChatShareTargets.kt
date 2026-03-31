package org.ole.planet.myplanet.model



data class ChatShareTargets(
    val community: TeamSummary?,
    val teams: List<TeamSummary>,
    val enterprises: List<TeamSummary>,
)
