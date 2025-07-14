package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.service.UploadManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun service(): Service
    fun sharedPreferences(): SharedPreferences
    fun uploadManager(): UploadManager
}
