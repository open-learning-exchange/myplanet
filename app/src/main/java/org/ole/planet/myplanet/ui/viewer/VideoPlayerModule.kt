package org.ole.planet.myplanet.ui.viewer

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import org.ole.planet.myplanet.datamanager.auth.AuthSessionUpdater

@Module
@InstallIn(ActivityComponent::class)
object VideoPlayerModule {
    @Provides
    fun provideAuthSessionUpdaterCallback(activity: Activity): AuthSessionUpdater.AuthCallback {
        return activity as AuthSessionUpdater.AuthCallback
    }
}
