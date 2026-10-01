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
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.repository.LifeCache
import org.ole.planet.myplanet.services.DownloadService
import org.ole.planet.myplanet.utils.AndroidKeyValueStore
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.KeyValueStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SurveyReminderPreferences

const val SURVEY_REMINDERS_PREFS_NAME = "survey_reminders"

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

    @Provides
    @Singleton
    @DownloadPreferences
    fun provideDownloadSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @AppPreferences
    fun provideAppKeyValueStore(@AppPreferences preferences: SharedPreferences): KeyValueStore {
        return AndroidKeyValueStore(preferences)
    }

    @Provides
    @Singleton
    @DefaultPreferences
    fun provideDefaultKeyValueStore(@DefaultPreferences preferences: SharedPreferences): KeyValueStore {
        return AndroidKeyValueStore(preferences)
    }

    @Provides
    @Singleton
    @SurveyReminderPreferences
    fun provideSurveyReminderKeyValueStore(@ApplicationContext context: Context): KeyValueStore {
        return AndroidKeyValueStore(context.getSharedPreferences(SURVEY_REMINDERS_PREFS_NAME, Context.MODE_PRIVATE))
    }

    @Provides
    @Singleton
    fun provideLifeCache(
        @AppPreferences store: KeyValueStore,
        json: Json
    ): LifeCache {
        return LifeCache(store, json)
    }
}
