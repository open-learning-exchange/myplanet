package org.ole.planet.myplanet.ui.teams

data class TeamStatus(
    val isMember: Boolean,
    val isLeader: Boolean,
    val hasPendingRequest: Boolean
)

data class TeamDetails(
    val _id: String?,
    val name: String?,
    val teamType: String?,
    val createdDate: Long?,
    val type: String?,
    val status: String?,
    val visitCount: Long,
    val teamStatus: TeamStatus?,
    val description: String?,
    val services: String?,
    val rules: String?,
    val teamId: String?
)
