package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@Deprecated("Use SharedInternalEntryPoint instead")
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServerUrlMapperEntryPoint {
    fun serverUrlMapper(): ServerUrlMapper
}
