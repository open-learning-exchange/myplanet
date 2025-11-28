package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.LifeRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LifeModule {
    @Provides
    @Singleton
    fun provideLifeRepository(databaseService: DatabaseService): LifeRepository {
        return LifeRepositoryImpl(databaseService)
    }
}
