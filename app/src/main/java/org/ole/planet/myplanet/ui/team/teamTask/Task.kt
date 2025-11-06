package org.ole.planet.myplanet.ui.team.teamTask

data class Task(
    val id: String?,
    val _id: String?,
    val _rev: String?,
    val title: String?,
    val description: String?,
    val link: String?,
    val sync: String?,
    val teamId: String?,
    val isUpdated: Boolean,
    val assignee: String?,
    val deadline: Long,
    val completedTime: Long,
    val status: String?,
    val completed: Boolean,
    val isNotified: Boolean,
    val assigneeName: String?
)
