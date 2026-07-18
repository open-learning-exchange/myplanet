package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.utils.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseService(
        dispatcherProvider: DispatcherProvider,
        appDatabase: AppDatabase,
    ): DatabaseService {
        return DatabaseService(dispatcherProvider, appDatabase)
    }
}
