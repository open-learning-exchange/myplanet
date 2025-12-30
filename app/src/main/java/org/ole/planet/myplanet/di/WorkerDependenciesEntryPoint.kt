package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDependenciesEntryPoint {
    fun databaseService(): DatabaseService
    fun userProfileDbHandler(): UserProfileDbHandler
    fun uploadManager(): UploadManager
    fun teamsRepository(): TeamsRepository
    fun submissionRepository(): SubmissionsRepository
}
