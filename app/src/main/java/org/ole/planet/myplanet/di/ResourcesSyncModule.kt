package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.ResourcesSyncRepository

@Module
@InstallIn(SingletonComponent::class)
object ResourcesSyncModule {
    @Provides
    fun provideResourcesSyncRepository(repository: ResourcesRepository): ResourcesSyncRepository = repository
}
