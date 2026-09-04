package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatSyncWriter
import org.ole.planet.myplanet.repository.CommunitySyncWriter
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackSyncWriter
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SyncTimeLogger

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private const val APPLICATION_SCOPE_LOG_TAG = "ApplicationScope"

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope {
        // Everything launched here is fire-and-forget background work (logging, warm-ups,
        // sync scheduling, network state). Without a handler a throw in any of it reaches
        // the default uncaught-exception handler, which persists a crash log and drops the
        // user on the home screen — losing the screen they were on over a failed log write.
        val logFailure = CoroutineExceptionHandler { _, failure ->
            Log.e(APPLICATION_SCOPE_LOG_TAG, "application scope work failed", failure)
        }
        return CoroutineScope(SupervisorJob() + dispatcherProvider.io + logFailure)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context,
        @AppPreferences preferences: SharedPreferences,
        userRepository: UserRepository,
        userSyncRepository: UserSyncRepository,
        healthRepository: HealthRepository,
        @ApplicationScope appScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider
    ): UploadToShelfService {
        return UploadToShelfService(context, preferences, userRepository, userSyncRepository, healthRepository, appScope, dispatcherProvider)
    }

    @Provides
    @Singleton
    fun provideTransactionSyncManager(
        apiInterface: ApiInterface,
        @ApplicationContext context: Context,
        voicesRepository: VoicesRepository,
        chatRepository: ChatSyncWriter,
        feedbackRepository: FeedbackSyncWriter,
        sharedPrefManager: SharedPrefManager,
        userRepository: UserRepository,
        userSyncRepository: UserSyncRepository,
        activitiesRepository: ActivitiesRepository,
        teamsSyncRepository: Lazy<TeamsSyncRepository>,
        notificationsRepository: NotificationsRepository,
        tagsRepository: TagsRepository,
        ratingsRepository: RatingsRepository,
        submissionsRepository: SubmissionsRepository,
        coursesRepository: CoursesRepository,
        communityRepository: CommunitySyncWriter,
        healthRepository: HealthRepository,
        progressRepository: ProgressRepository,
        surveysRepository: SurveysRepository,
        dispatcherProvider: DispatcherProvider,
        userSessionManager: UserSessionManager,
        syncTimeLogger: SyncTimeLogger
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, context, voicesRepository, chatRepository, feedbackRepository, sharedPrefManager, userRepository, userSyncRepository, activitiesRepository, teamsSyncRepository, notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, dispatcherProvider, userSessionManager, syncTimeLogger)
    }
}
