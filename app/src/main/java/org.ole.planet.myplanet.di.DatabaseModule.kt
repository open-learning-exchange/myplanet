package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.data.DatabaseService

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseService(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider
    ): DatabaseService {
        return DatabaseService(context, dispatcherProvider)
    }

    // Realm initialization is handled in DatabaseService
}
