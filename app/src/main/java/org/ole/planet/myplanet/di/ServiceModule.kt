package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        managerSync: ManagerSync
    ): SyncManager {
        return SyncManager(context, databaseService, preferences, managerSync)
    }

    @Provides
    @Singleton
    fun provideUploadManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences
    ): UploadManager {
        return UploadManager(context, databaseService, preferences)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context
    ): UploadToShelfService {
        return UploadToShelfService(context)
    }
}