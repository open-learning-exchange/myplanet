package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.ApiClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApiClientEntryPoint {
    fun apiClient(): ApiClient
}
