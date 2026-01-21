package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.repository.JoinedMemberData

interface OnMemberActionListener {
    fun onRemoveMember(member: JoinedMemberData, position: Int)
    fun onMakeLeader(member: JoinedMemberData)
    fun onLeaveTeam()
}
