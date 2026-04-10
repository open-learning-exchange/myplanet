package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.sync.SyncManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkDependenciesEntryPoint {
    fun apiClient(): ApiClient
    fun apiInterface(): ApiInterface
    fun syncManager(): SyncManager
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    fun retryQueue(): RetryQueue
}
