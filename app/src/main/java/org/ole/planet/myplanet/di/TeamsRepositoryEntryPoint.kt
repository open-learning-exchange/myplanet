package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.TeamsRepository

@Deprecated("Use SharedInternalEntryPoint instead")
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TeamsRepositoryEntryPoint {
    fun teamsRepository(): TeamsRepository
}
