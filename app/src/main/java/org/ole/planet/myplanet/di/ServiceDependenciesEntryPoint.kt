package org.ole.planet.myplanet.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.services.BroadcastService
import org.ole.planet.myplanet.services.retry.RetryQueue

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceDependenciesEntryPoint {
    fun broadcastService(): BroadcastService
    fun retryQueue(): RetryQueue
}

fun getBroadcastService(context: Context): BroadcastService {
    val hiltEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ServiceDependenciesEntryPoint::class.java
    )
    return hiltEntryPoint.broadcastService()
}
