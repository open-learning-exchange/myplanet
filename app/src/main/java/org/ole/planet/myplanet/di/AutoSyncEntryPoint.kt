package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.ConfigurationRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.service.sync.SyncManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
    fun syncManager(): SyncManager
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    fun configurationRepository(): ConfigurationRepository
    @AppPreferences
    fun sharedPreferences(): SharedPreferences
}
