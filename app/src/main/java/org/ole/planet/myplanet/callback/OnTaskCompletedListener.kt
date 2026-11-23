package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.TeamTask

interface OnTaskCompletedListener {
    fun onEdit(task: TeamTask?)
    fun onDelete(task: TeamTask?)
    fun onClickMore(realmTeamTask: TeamTask?)
    fun onStatusChange(task: TeamTask?, newStatus: String)
}
