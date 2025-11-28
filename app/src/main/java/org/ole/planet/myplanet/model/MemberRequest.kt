package org.ole.planet.myplanet.model

data class MemberRequest(
    val userId: String,
    val userName: String,
    val isUserTeamLeader: Boolean,
    val canModerate: Boolean,
    val teamMemberCount: Int,
    val isCurrentUser: Boolean
)
