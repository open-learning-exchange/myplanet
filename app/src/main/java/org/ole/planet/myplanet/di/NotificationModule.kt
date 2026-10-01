package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.TimeProvider

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        timeProvider: TimeProvider
    ): NotificationUtils.NotificationManager = NotificationUtils.NotificationManager(context, timeProvider)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun notificationManager(): NotificationUtils.NotificationManager
}
