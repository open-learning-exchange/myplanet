package org.ole.planet.myplanet.model

data class ChatShareTargets(
    val community: RealmMyTeam?,
    val teams: List<RealmMyTeam>,
    val enterprises: List<RealmMyTeam>,
)
