package org.ole.planet.myplanet.ui.team.resources

interface  ResourcesUpdateListner {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
