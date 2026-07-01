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
import io.realm.Realm
import io.realm.RealmConfiguration
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRealmConfiguration(@ApplicationContext context: Context): RealmConfiguration {
        Realm.init(context)
        val targetLogLevel = if (org.ole.planet.myplanet.BuildConfig.DEBUG) io.realm.log.LogLevel.DEBUG else io.realm.log.LogLevel.ERROR
        if (io.realm.log.RealmLog.getLevel() != targetLogLevel) {
            io.realm.log.RealmLog.setLevel(targetLogLevel)
        }
        val currentConfig = Realm.getDefaultConfiguration()
        return if (currentConfig == null || currentConfig.realmDirectory.name == Realm.DEFAULT_REALM_NAME) {
            val config = RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .schemaVersion(13)
                .migration(org.ole.planet.myplanet.data.RealmMigrations())
                .build()
            Realm.setDefaultConfiguration(config)
            config
        } else {
            currentConfig
        }
    }

    @Provides
    @Singleton
    fun provideDatabaseService(dispatcherProvider: DispatcherProvider, realmConfiguration: RealmConfiguration): DatabaseService {
        return DatabaseService(dispatcherProvider, realmConfiguration)
    }

    @Provides
    @Singleton
    @RealmDispatcher
    fun provideRealmDispatcher(provider: RealmDispatcherProvider): CoroutineDispatcher {
        return provider
    }
}
