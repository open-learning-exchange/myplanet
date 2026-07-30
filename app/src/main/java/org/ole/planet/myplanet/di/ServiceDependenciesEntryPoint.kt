package org.ole.planet.myplanet.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.BroadcastService
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.sync.SyncManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceDependenciesEntryPoint {
    fun broadcastService(): BroadcastService
    fun apiInterface(): ApiInterface
    fun syncManager(): SyncManager
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    fun retryQueue(): RetryQueue
}

fun getBroadcastService(context: Context): BroadcastService {
    val hiltEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ServiceDependenciesEntryPoint::class.java
    )
    return hiltEntryPoint.broadcastService()
}
