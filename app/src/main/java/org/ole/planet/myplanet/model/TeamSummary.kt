package org.ole.planet.myplanet.model



data class TeamSummary(
    val _id: String,
    val name: String,
    val teamType: String?,
    val teamPlanetCode: String?,
    val createdDate: Long?,
    val type: String?,
    val status: String?,
    val teamId: String?,
    val description: String?,
    val services: String?,
    val rules: String?
)
