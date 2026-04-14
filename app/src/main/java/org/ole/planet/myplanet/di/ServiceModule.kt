package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.ImprovedSyncManager
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.services.sync.TransactionSyncManager

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
        apiInterface: ApiInterface,
        improvedSyncManager: Lazy<ImprovedSyncManager>,
        transactionSyncManager: TransactionSyncManager,
        resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository,
        loginSyncManager: org.ole.planet.myplanet.services.sync.LoginSyncManager,
        @ApplicationScope scope: CoroutineScope,
        activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
        communityRepository: org.ole.planet.myplanet.repository.CommunityRepository,
        dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider,
        teamsRepository: org.ole.planet.myplanet.repository.TeamsRepository
    ): SyncManager {
        return SyncManager(context, databaseService, sharedPrefManager, apiInterface, improvedSyncManager, transactionSyncManager, resourcesRepository, loginSyncManager, scope, activitiesRepository, communityRepository, dispatcherProvider, teamsRepository)
    }

    @Provides
    @Singleton
    fun provideUploadManager(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        submissionsRepository: SubmissionsRepository,
        sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
        gson: Gson,
        uploadCoordinator: org.ole.planet.myplanet.services.upload.UploadCoordinator,
        personalsRepository: PersonalsRepository,
        userRepository: org.ole.planet.myplanet.repository.UserRepository,
        chatRepository: org.ole.planet.myplanet.repository.ChatRepository,
        voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
        uploadConfigs: org.ole.planet.myplanet.services.upload.UploadConfigs,
        resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository,
        teamsRepository: Lazy<org.ole.planet.myplanet.repository.TeamsRepository>,
        activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
        apiInterface: ApiInterface,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider
    ): UploadManager {
        return UploadManager(context, databaseService, submissionsRepository, sharedPrefManager, gson, uploadCoordinator, personalsRepository, userRepository, chatRepository, voicesRepository, uploadConfigs, resourcesRepository, teamsRepository, apiInterface, activitiesRepository, dispatcherProvider, scope)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context,
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
        resourcesRepository: org.ole.planet.myplanet.repository.ResourcesRepository,
        coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository,
        userRepository: org.ole.planet.myplanet.repository.UserRepository,
        healthRepository: org.ole.planet.myplanet.repository.HealthRepository,
        @ApplicationScope appScope: CoroutineScope,
        dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider,
        apiInterface: org.ole.planet.myplanet.data.api.ApiInterface
    ): UploadToShelfService {
        return UploadToShelfService(context, databaseService, preferences, sharedPrefManager, resourcesRepository, coursesRepository, userRepository, healthRepository, appScope, dispatcherProvider, apiInterface)
    }

    @Provides
    @Singleton
    fun provideTransactionSyncManager(
        apiInterface: ApiInterface,
        databaseService: DatabaseService,
        @ApplicationContext context: Context,
        voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
        chatRepository: org.ole.planet.myplanet.repository.ChatRepository,
        feedbackRepository: org.ole.planet.myplanet.repository.FeedbackRepository,
        sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
        userRepository: org.ole.planet.myplanet.repository.UserRepository,
        activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
        teamsRepository: dagger.Lazy<org.ole.planet.myplanet.repository.TeamsRepository>,
        notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,
        tagsRepository: org.ole.planet.myplanet.repository.TagsRepository,
        ratingsRepository: org.ole.planet.myplanet.repository.RatingsRepository,
        submissionsRepository: org.ole.planet.myplanet.repository.SubmissionsRepository,
        coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository,
        communityRepository: org.ole.planet.myplanet.repository.CommunityRepository,
        healthRepository: org.ole.planet.myplanet.repository.HealthRepository,
        progressRepository: org.ole.planet.myplanet.repository.ProgressRepository,
        surveysRepository: org.ole.planet.myplanet.repository.SurveysRepository,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, databaseService, context, voicesRepository, chatRepository, feedbackRepository, sharedPrefManager, userRepository, activitiesRepository, teamsRepository, notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, scope, dispatcherProvider)
    }
}
