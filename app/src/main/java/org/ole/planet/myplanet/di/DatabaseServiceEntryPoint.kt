package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.DatabaseService

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseServiceEntryPoint {
    fun databaseService(): DatabaseService
}
