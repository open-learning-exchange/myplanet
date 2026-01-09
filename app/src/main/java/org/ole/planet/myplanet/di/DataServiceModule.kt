package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.DataService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataServiceModule {

    @Provides
    @Singleton
    fun provideDataService(@ApplicationContext context: Context): DataService {
        return DataService(context)
    }
}