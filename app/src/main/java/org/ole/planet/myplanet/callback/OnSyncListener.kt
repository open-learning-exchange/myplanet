package org.ole.planet.myplanet.callback

interface OnSyncListener {
    @JvmSuppressWildcards
    fun onSyncStarted()
    @JvmSuppressWildcards
    fun onSyncComplete()
    @JvmSuppressWildcards
    fun onSyncFailed(msg: String?)
}
