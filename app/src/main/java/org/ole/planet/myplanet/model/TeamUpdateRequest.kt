package org.ole.planet.myplanet.model

data class TeamUpdateRequest(
    val teamId: String,
    val name: String,
    val description: String,
    val services: String,
    val rules: String,
    val updatedBy: String? = null,
    val profileImage: String? = null
)

data class TeamDetailsUpdateRequest(
    val teamId: String,
    val name: String,
    val description: String,
    val services: String,
    val rules: String,
    val teamType: String,
    val isPublic: Boolean,
    val createdBy: String,
    val profileImage: String? = null
)