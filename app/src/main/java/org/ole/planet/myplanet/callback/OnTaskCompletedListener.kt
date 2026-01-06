package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTeamTask

interface OnTaskCompletedListener {
    fun onCheckChange(realmTeamTask: RealmTeamTask?, b: Boolean)
    fun onEdit(task: RealmTeamTask?)
    fun onDelete(task: RealmTeamTask?)
    fun onClickMore(realmTeamTask: RealmTeamTask?)
}
