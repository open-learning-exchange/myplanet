package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreDependenciesEntryPoint {
    @ApplicationScope fun applicationScope(): CoroutineScope
    fun sharedPrefManager(): SharedPrefManager
    fun userSessionManager(): UserSessionManager
    fun serverUrlMapper(): ServerUrlMapper
    fun dispatcherProvider(): DispatcherProvider
    fun apkLogDao(): ApkLogDao
}
