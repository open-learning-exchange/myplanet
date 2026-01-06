package org.ole.planet.myplanet.ui.teams.callback

import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.teams.TeamDetails

interface OnTeamActionsListener {
    fun onLeaveTeam(team: TeamDetails, user: RealmUserModel?)
    fun onRequestToJoin(team: TeamDetails, user: RealmUserModel?)
}
