package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamDetails

interface OnTeamActionsListener {
    fun onLeaveTeam(team: TeamDetails, user: RealmUser?)
    fun onRequestToJoin(team: TeamDetails, user: RealmUser?)
}
