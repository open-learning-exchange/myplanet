package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.HealthRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository
}
