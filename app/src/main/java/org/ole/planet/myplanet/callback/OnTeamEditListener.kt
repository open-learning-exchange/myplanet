package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.TeamDetails

interface OnTeamEditListener {
    fun onEditTeam(team: TeamDetails?)
}
