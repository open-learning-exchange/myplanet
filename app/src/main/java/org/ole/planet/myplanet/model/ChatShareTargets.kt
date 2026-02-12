package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.model.dto.Team

data class ChatShareTargets(
    val community: Team?,
    val teams: List<RealmMyTeam>,
    val enterprises: List<RealmMyTeam>,
)
