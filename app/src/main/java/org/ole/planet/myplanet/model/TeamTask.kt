package org.ole.planet.myplanet.model

data class TeamTask(
    val id: String,
    val title: String,
    val description: String,
    val deadline: Long,
    val completed: Boolean,
    val assignee: String?
)

fun RealmTeamTask.toTeamTask(): TeamTask {
    return TeamTask(
        id = this.id.orEmpty(),
        title = this.title.orEmpty(),
        description = this.description.orEmpty(),
        deadline = this.deadline,
        completed = this.completed,
        assignee = this.assignee
    )
}
