package org.ole.planet.myplanet.di

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseService(@ApplicationContext context: Context, dispatcherProvider: DispatcherProvider): DatabaseService {
        return DatabaseService(context, dispatcherProvider)
    }

    @Provides
    @Singleton
    @RealmDispatcher
    fun provideRealmDispatcher(): CoroutineDispatcher {
        // App-level shutdown hook ownership
        return java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "RealmQueryThread")
        }.asCoroutineDispatcher()
    }

    // Realm initialization is handled in DatabaseService
}
