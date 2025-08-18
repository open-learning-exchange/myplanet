package org.ole.planet.myplanet.callback

interface SyncListener {
    @JvmSuppressWildcards
    fun onSyncStarted()
    @JvmSuppressWildcards
    fun onSyncComplete()
    @JvmSuppressWildcards
    fun onSyncFailed(msg: String?)
}
