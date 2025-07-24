package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import org.ole.planet.myplanet.datamanager.DatabaseService
import javax.inject.Singleton

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
    fun provideRealmConfiguration(@ApplicationContext context: Context): RealmConfiguration {
        Realm.init(context)
        RealmLog.setLevel(LogLevel.DEBUG)
        return RealmConfiguration.Builder()
            .name(Realm.DEFAULT_REALM_NAME)
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(4)
            .allowWritesOnUiThread(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRealm(realmConfiguration: RealmConfiguration): Realm {
        Realm.setDefaultConfiguration(realmConfiguration)
        return Realm.getDefaultInstance()
    }
}