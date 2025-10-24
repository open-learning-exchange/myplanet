package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDependenciesEntryPoint {
    fun databaseService(): DatabaseService
    fun userProfileDbHandler(): UserProfileDbHandler
    fun uploadManager(): UploadManager
    fun submissionRepository(): SubmissionRepository
}
