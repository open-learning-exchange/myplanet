package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.utils.UriAttachment

data class CreateTeamRequest(
    val name: String,
    val description: String,
    val services: String,
    val rules: String,
    val teamType: String,
    val isPublic: Boolean,
    val category: String?,
    val cover: UriAttachment? = null
)
