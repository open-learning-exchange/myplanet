package org.ole.planet.myplanet.model

data class Team(
    val _id: String,
    val _rev: String?,
    val name: String?,
    val title: String?,
    val description: String?,
    val limit: Int,
    val status: String?,
    val teamId: String?,
    val teamType: String?,
    val type: String?,
    val services: String?,
    val rules: String?,
    val parentCode: String?,
    val createdBy: String?,
    val userPlanetCode: String?,
    val teamPlanetCode: String?,
    val isLeader: Boolean,
    val isPublic: Boolean,
    val amount: Int,
    val createdDate: Long,
    val updatedDate: Long,
    val requests: String?,
    val courses: List<String>?,
    val route: String?
)
