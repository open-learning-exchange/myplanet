package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseService(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider,
        appDatabase: AppDatabase,
    ): DatabaseService {
        return DatabaseService(context, dispatcherProvider, appDatabase)
    }

    @Provides
    @Singleton
    @RealmDispatcher
    fun provideRealmDispatcher(provider: RealmDispatcherProvider): CoroutineDispatcher {
        return provider
    }

    // Realm initialization is handled in DatabaseService
}
