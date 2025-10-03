package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.service.UploadManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerDependenciesEntryPoint {
    fun databaseService(): DatabaseService
    fun uploadManager(): UploadManager
}
