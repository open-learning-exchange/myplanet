package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager

@Deprecated("Use SharedInternalEntryPoint instead")
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDependenciesEntryPoint {
    fun userSessionManager(): UserSessionManager
    fun uploadManager(): UploadManager
    fun teamsRepository(): TeamsRepository
    fun submissionsRepository(): SubmissionsRepository
    fun resourcesRepository(): ResourcesRepository
}
