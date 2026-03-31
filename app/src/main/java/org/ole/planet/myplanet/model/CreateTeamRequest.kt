package org.ole.planet.myplanet.model



data class CreateTeamRequest(
    val name: String,
    val description: String,
    val services: String,
    val rules: String,
    val teamType: String,
    val isPublic: Boolean,
    val category: String?
)
