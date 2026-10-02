package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsShareTargetsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.repository.VoicesShareRepository

@Module
@InstallIn(SingletonComponent::class)
object ChatShareModule {
    @Provides
    fun provideTeamsShareTargetsRepository(teamsRepository: TeamsRepository): TeamsShareTargetsRepository = teamsRepository

    @Provides
    fun provideVoicesShareRepository(voicesRepository: VoicesRepository): VoicesShareRepository = voicesRepository
}
