package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.RatingsRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRatingsRepository(
        ratingsRepositoryImpl: RatingsRepositoryImpl
    ): RatingsRepository
}