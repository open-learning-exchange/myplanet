package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreDependenciesEntryPoint {
    @ApplicationScope fun applicationScope(): CoroutineScope
    fun sharedPrefManager(): SharedPrefManager
    fun databaseService(): DatabaseService
    fun userSessionManager(): UserSessionManager
    fun serverUrlMapper(): ServerUrlMapper
}
