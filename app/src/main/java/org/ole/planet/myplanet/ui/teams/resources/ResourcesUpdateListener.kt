package org.ole.planet.myplanet.ui.teams.resources

interface  ResourcesUpdateListener {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
