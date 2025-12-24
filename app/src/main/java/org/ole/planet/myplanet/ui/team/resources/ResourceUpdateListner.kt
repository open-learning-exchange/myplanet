package org.ole.planet.myplanet.ui.team.resources

interface  ResourceUpdateListner {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
