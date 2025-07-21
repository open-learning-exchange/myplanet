package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.datamanager.ManagerSync

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    fun syncManager(): SyncManager
    fun managerSync(): ManagerSync
}
