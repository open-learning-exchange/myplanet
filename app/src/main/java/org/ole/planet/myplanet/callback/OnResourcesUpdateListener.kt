package org.ole.planet.myplanet.callback

interface OnResourcesUpdateListener {
    fun onResourceListUpdated()
    fun onResourceUpdateFailed(messageResId: Int)
}
