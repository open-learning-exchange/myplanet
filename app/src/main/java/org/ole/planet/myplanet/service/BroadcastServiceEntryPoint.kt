package org.ole.planet.myplanet.service

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BroadcastServiceEntryPoint {
    fun broadcastService(): BroadcastService
}

fun getBroadcastService(context: Context): BroadcastService {
    val hiltEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        BroadcastServiceEntryPoint::class.java
    )
    return hiltEntryPoint.broadcastService()
}
