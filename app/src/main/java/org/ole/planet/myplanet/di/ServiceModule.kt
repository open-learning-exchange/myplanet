package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface
    ): SyncManager {
        return SyncManager(context, databaseService, preferences, apiInterface)
    }

    @Provides
    @Singleton
    fun provideUploadManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface,
    ): UploadManager {
        return UploadManager(context, databaseService, preferences, apiInterface)
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
}
