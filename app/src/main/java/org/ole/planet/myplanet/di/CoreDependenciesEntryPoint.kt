package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.repository.DiagnosticsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreDependenciesEntryPoint {
    @ApplicationScope fun applicationScope(): CoroutineScope
    fun sharedPrefManager(): SharedPrefManager
    fun serverUrlMapper(): ServerUrlMapper
    fun dispatcherProvider(): DispatcherProvider
    fun diagnosticsRepository(): DiagnosticsRepository
    fun timeProvider(): TimeProvider
}
