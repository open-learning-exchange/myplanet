package org.ole.planet.myplanet.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Singleton
    @Provides
    fun provideRealDatabase(
        @ApplicationContext appContext: Context
    ): Realm = DatabaseService(appContext).realmInstance
}