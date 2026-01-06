package org.ole.planet.myplanet.callback

interface  ResourcesUpdateListener {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
