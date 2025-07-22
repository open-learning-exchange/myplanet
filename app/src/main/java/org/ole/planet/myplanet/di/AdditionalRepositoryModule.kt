package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.data.NewsRepository
import org.ole.planet.myplanet.data.NewsRepositoryImpl
import org.ole.planet.myplanet.datamanager.DatabaseService

@Module
@InstallIn(SingletonComponent::class)
object AdditionalRepositoryModule {
    @Provides
    @Singleton
    fun provideNewsRepository(databaseService: DatabaseService): NewsRepository {
        return NewsRepositoryImpl(databaseService)
    }
}
