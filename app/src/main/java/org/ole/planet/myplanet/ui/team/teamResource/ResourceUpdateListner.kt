package org.ole.planet.myplanet.ui.team.teamResource

interface  ResourceUpdateListner {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
