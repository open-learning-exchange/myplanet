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
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRealmConfiguration(): io.realm.RealmConfiguration {
        return io.realm.RealmConfiguration.Builder()
            .name(io.realm.Realm.DEFAULT_REALM_NAME)
            .schemaVersion(12)
            .migration(org.ole.planet.myplanet.data.RealmMigrations())
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabaseService(@ApplicationContext context: Context, dispatcherProvider: DispatcherProvider, realmConfiguration: io.realm.RealmConfiguration): DatabaseService {
        return DatabaseService(context, dispatcherProvider, realmConfiguration)
    }

    @Provides
    @Singleton
    @RealmDispatcher
    fun provideRealmDispatcher(provider: RealmDispatcherProvider): CoroutineDispatcher {
        return provider
    }

    // Realm initialization is handled in DatabaseService
}
