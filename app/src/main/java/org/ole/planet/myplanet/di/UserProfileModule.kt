package org.ole.planet.myplanet.di


import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.UserProfileDbHandler
@Module
@InstallIn(SingletonComponent::class)
object UserProfileModule {

    @Provides
    @Singleton
    fun provideUserProfileDbHandler(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences
    ): UserProfileDbHandler {
        return UserProfileDbHandler(context, databaseService, preferences)
    }
}
