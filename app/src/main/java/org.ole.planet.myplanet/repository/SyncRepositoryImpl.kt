package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.service.SyncManager

class SyncRepositoryImpl @Inject constructor(
    private val syncManager: SyncManager
) : SyncRepository {

    override fun startSync(type: String) {
        syncManager.start(null, type)
    }
}
