package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.SyncManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
    fun syncManager(): SyncManager
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    @AppPreferences
    fun sharedPreferences(): SharedPreferences
}
