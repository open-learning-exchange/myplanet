package org.ole.planet.myplanet.ui.team.teamMember

import org.ole.planet.myplanet.model.RealmUserModel

data class MemberRequest(
    val user: RealmUserModel,
    val teamId: String,
    val canModerate: Boolean,
    val isUserLoggedIn: Boolean,
    val memberCount: Int,
)
