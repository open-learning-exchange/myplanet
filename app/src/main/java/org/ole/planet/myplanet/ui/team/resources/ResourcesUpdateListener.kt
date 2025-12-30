package org.ole.planet.myplanet.ui.team.resources

interface  ResourcesUpdateListener {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
