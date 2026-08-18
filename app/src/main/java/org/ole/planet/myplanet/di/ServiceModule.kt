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
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    private const val TAG = "ApplicationScope"

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope {
        // Work launched here is fire-and-forget, so without a handler any failure reaches the
        // default uncaught handler and kills the process. Log and degrade instead: a failed
        // background upload or warm-up must not take the app down.
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught exception in application scope", throwable)
        }
        return CoroutineScope(SupervisorJob() + dispatcherProvider.io + exceptionHandler)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context,
        @AppPreferences preferences: SharedPreferences,
        sharedPrefManager: SharedPrefManager,
        userRepository: UserRepository,
        userSyncRepository: UserSyncRepository,
        healthRepository: HealthRepository,
        @ApplicationScope appScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider
    ): UploadToShelfService {
        return UploadToShelfService(context, preferences, sharedPrefManager, userRepository, userSyncRepository, healthRepository, appScope, dispatcherProvider)
    }

    @Provides
    @Singleton
    fun provideTransactionSyncManager(
        apiInterface: ApiInterface,
        @ApplicationContext context: Context,
        voicesRepository: VoicesRepository,
        chatRepository: ChatRepository,
        feedbackRepository: FeedbackRepository,
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
        communityRepository: CommunityRepository,
        healthRepository: HealthRepository,
        progressRepository: ProgressRepository,
        surveysRepository: SurveysRepository,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        userSessionManager: org.ole.planet.myplanet.services.UserSessionManager
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, context, voicesRepository, chatRepository, feedbackRepository, sharedPrefManager, userRepository, userSyncRepository, activitiesRepository, teamsSyncRepository, notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, scope, dispatcherProvider, userSessionManager)
    }
}
