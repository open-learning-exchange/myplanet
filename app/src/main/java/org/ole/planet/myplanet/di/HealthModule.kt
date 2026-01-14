package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.HealthRepositoryImpl

@Module
@InstallIn(ViewModelComponent::class)
abstract class HealthModule {
    @Binds
    abstract fun bindHealthRepository(
        healthRepositoryImpl: HealthRepositoryImpl
    ): HealthRepository
}
