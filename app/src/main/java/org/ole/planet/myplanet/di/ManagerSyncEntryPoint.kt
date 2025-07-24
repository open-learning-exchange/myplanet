package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.ManagerSync

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManagerSyncEntryPoint {
    fun managerSync(): ManagerSync
}
