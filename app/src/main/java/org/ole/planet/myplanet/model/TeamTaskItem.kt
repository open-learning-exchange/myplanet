package org.ole.planet.myplanet.model

data class TeamTaskItem(
    val id: String,
    val title: String,
    val description: String,
    val deadline: Long,
    val completed: Boolean,
    val assignee: String?
)
