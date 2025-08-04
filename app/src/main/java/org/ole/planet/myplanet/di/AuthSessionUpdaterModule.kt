package org.ole.planet.myplanet.di

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import org.ole.planet.myplanet.utilities.AuthSessionUpdater

@Module
@InstallIn(ActivityComponent::class)
object AuthSessionUpdaterModule {
    @Provides
    fun provideAuthCallback(activity: Activity): AuthSessionUpdater.AuthCallback {
        return activity as AuthSessionUpdater.AuthCallback
    }
}
