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
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.data.DatabaseService

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseService(@ApplicationContext context: Context): DatabaseService {
        return DatabaseService(context)
    }

    @Provides
    @Singleton
    @RealmDispatcher
    fun provideRealmDispatcher(): CoroutineDispatcher {
        val handlerThread = HandlerThread("RealmQueryThread")
        handlerThread.start()
        return Handler(handlerThread.looper).asCoroutineDispatcher()
    }

    // Realm initialization is handled in DatabaseService
}
