package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.retry.RetryQueue

@Deprecated("Use SharedInternalEntryPoint instead")
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RetryQueueEntryPoint {
    fun retryQueue(): RetryQueue
    fun apiInterface(): ApiInterface
}
