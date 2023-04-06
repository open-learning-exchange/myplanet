package org.ole.planet.myplanet.di

import android.content.Context
import android.os.storage.StorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@InstallIn(SingletonComponent::class)
@Module
object AndroidModule {
   private const val NumberOfThreadsInThreadPool =4
   @Provides
   @IOExecutor
   fun provideIoExecutor():ExecutorService{
       return Executors.newFixedThreadPool(NumberOfThreadsInThreadPool)
   }
   @Provides
   fun provideStorageManager(@ApplicationContext context:Context):StorageManager{
      return context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
   }
}