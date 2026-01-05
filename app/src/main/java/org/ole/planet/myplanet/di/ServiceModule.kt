package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.service.sync.ImprovedSyncManager
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.service.sync.TransactionSyncManager
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface,
        improvedSyncManager: Lazy<ImprovedSyncManager>,
        transactionSyncManager: TransactionSyncManager,
        @ApplicationScope scope: CoroutineScope
    ): SyncManager {
        return SyncManager(context, databaseService, preferences, apiInterface, improvedSyncManager, transactionSyncManager, scope)
    }

    @Provides
    @Singleton
    fun provideUploadManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        submissionsRepository: SubmissionsRepository,
        @AppPreferences preferences: SharedPreferences,
        gson: Gson
    ): UploadManager {
        return UploadManager(context, databaseService, submissionsRepository, preferences, gson)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences
    ): UploadToShelfService {
        return UploadToShelfService(context, databaseService, preferences)
    }

    @Provides
    @Singleton
    fun provideTransactionSyncManager(
        apiInterface: ApiInterface,
        databaseService: DatabaseService,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, databaseService, context, scope)
    }
}
