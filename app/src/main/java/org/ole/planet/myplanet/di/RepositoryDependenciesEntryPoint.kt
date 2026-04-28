package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryDependenciesEntryPoint {
    fun userRepository(): UserRepository
    fun configurationsRepository(): ConfigurationsRepository
    fun communityRepository(): CommunityRepository
    fun teamsRepository(): TeamsRepository
    fun submissionsRepository(): SubmissionsRepository
    fun resourcesRepository(): ResourcesRepository
    fun surveysRepository(): SurveysRepository
}
