package org.ole.planet.myplanet.repository

interface SyncRepository {
    fun startSync(type: String)
}
