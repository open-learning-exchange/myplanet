package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.repository.ApkLogRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreDependenciesEntryPoint {
    @ApplicationScope fun applicationScope(): CoroutineScope
    fun sharedPrefManager(): SharedPrefManager
    fun userSessionManager(): UserSessionManager
    fun serverUrlMapper(): ServerUrlMapper
    fun dispatcherProvider(): DispatcherProvider
    fun apkLogRepository(): ApkLogRepository
    fun timeProvider(): TimeProvider
    fun resourcesRepository(): ResourcesRepository
}
