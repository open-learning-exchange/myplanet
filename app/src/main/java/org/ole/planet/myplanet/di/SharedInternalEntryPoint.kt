package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.*
import org.ole.planet.myplanet.services.*
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SharedInternalEntryPoint {
    @ApplicationScope fun applicationScope(): CoroutineScope
    fun apiClient(): ApiClient
    fun apiInterface(): ApiInterface
    fun syncManager(): SyncManager
    fun uploadManager(): UploadManager
    fun uploadToShelfService(): UploadToShelfService
    fun sharedPrefManager(): SharedPrefManager
    fun broadcastService(): BroadcastService
    fun databaseService(): DatabaseService
    fun userRepository(): UserRepository
    fun configurationsRepository(): ConfigurationsRepository
    fun communityRepository(): CommunityRepository
    fun retryQueue(): RetryQueue
    fun serverUrlMapper(): ServerUrlMapper
    fun teamsRepository(): TeamsRepository
    fun userSessionManager(): UserSessionManager
    fun submissionsRepository(): SubmissionsRepository
    fun resourcesRepository(): ResourcesRepository
}
