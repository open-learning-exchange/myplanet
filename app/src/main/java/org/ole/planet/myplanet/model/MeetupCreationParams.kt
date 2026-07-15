package org.ole.planet.myplanet.model

data class MeetupCreationParams(
    val title: String,
    val meetupLink: String,
    val description: String,
    val location: String,
    val startTime: String,
    val endTime: String,
    val recurringText: String?,
    val teamPlanetCode: String?,
    val userName: String?,
    val startMillis: Long,
    val endMillis: Long,
    val teamId: String
)