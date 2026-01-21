package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultPreferences

@Module
@InstallIn(SingletonComponent::class)
object SharedPreferencesModule {

    @Provides
    @Singleton
    @AppPreferences
    fun provideAppSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @DefaultPreferences
    fun provideDefaultSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}
