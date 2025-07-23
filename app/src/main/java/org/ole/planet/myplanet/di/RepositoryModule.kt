package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import java.util.Date
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNotification
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface
    ): UserRepository {
        return UserRepositoryImpl(databaseService, preferences, apiInterface)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): LibraryRepository {
        return LibraryRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): CourseRepository {
        return CourseRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideDashboardRepository(
        databaseService: DatabaseService
    ): DashboardRepository {
        return DashboardRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideNetworkRepository(): NetworkRepository {
        return NetworkRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideChatRepository(): ChatRepository {
        return ChatRepositoryImpl()
    }
}

// User Repository
interface UserRepository {
    suspend fun getUserProfile(): String?
    suspend fun saveUserData(data: String)
    fun getRealm(): Realm
    fun getCurrentUser(): RealmUserModel?
}

class UserRepositoryImpl(
    private val databaseService: DatabaseService,
    private val preferences: SharedPreferences,
    private val apiInterface: ApiInterface
) : UserRepository {

    override suspend fun getUserProfile(): String? {
        return preferences.getString("user_profile", null)
    }

    override suspend fun saveUserData(data: String) {
        preferences.edit().putString("user_profile", data).apply()
    }

    override fun getRealm(): Realm {
        return databaseService.realmInstance
    }

    override fun getCurrentUser(): RealmUserModel? {
        return databaseService.realmInstance.where(RealmUserModel::class.java).findFirst()
    }
}

// Library Repository
interface LibraryRepository {
    fun getAllLibraryItems(): List<RealmMyLibrary>
    fun getLibraryItemById(id: String): RealmMyLibrary?
    fun getOfflineLibraryItems(): List<RealmMyLibrary>
}

class LibraryRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : LibraryRepository {

    override fun getAllLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java).findAll()
    }

    override fun getLibraryItemById(id: String): RealmMyLibrary? {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("resourceOffline", true)
            .findAll()
    }
}

// Course Repository
interface CourseRepository {
    fun getAllCourses(): List<RealmMyCourse>
    fun getCourseById(id: String): RealmMyCourse?
    fun getEnrolledCourses(): List<RealmMyCourse>
}

class CourseRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : CourseRepository {

    override fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java).findAll()
    }

    override fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("courseId", id)
            .findFirst()
    }

    override fun getEnrolledCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("userId", getCurrentUserId())
            .findAll()
    }

    private fun getCurrentUserId(): String {
        return databaseService.realmInstance.where(RealmUserModel::class.java)
            .findFirst()?.id ?: ""
    }
}

// Dashboard Repository
interface DashboardRepository {
    fun updateResourceNotification(userId: String?)
    fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?)
    fun getPendingSurveys(userId: String?): List<RealmSubmission>
    fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String>
    fun getUnreadNotificationsSize(userId: String?): Int
}

class DashboardRepositoryImpl(
    private val databaseService: DatabaseService
) : DashboardRepository {

    override fun updateResourceNotification(userId: String?) {
        databaseService.withRealm { realm ->
            realm.executeTransaction { txRealm ->
                val resourceCount = BaseResourceFragment.getLibraryList(txRealm, userId).size
                if (resourceCount > 0) {
                    val existingNotification = txRealm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()
                    if (existingNotification != null) {
                        existingNotification.message = "$resourceCount"
                        existingNotification.relatedId = "$resourceCount"
                    } else {
                        createNotificationIfNotExistsTx(txRealm, "resource", "$resourceCount", "$resourceCount", userId)
                    }
                } else {
                    txRealm.where(RealmNotification::class.java)
                        .equalTo("userId", userId)
                        .equalTo("type", "resource")
                        .findFirst()?.deleteFromRealm()
                }
            }
        }
    }

    private fun createNotificationIfNotExistsTx(
        realm: Realm,
        type: String,
        message: String,
        relatedId: String?,
        userId: String?
    ) {
        val existingNotification = realm.where(RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()

        if (existingNotification == null) {
            realm.createObject(RealmNotification::class.java, "${UUID.randomUUID()}").apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = Date()
            }
        }
    }

    override fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) {
        databaseService.withRealm { realm ->
            realm.executeTransaction { txRealm ->
                createNotificationIfNotExistsTx(txRealm, type, message, relatedId, userId)
            }
        }
    }

    override fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return databaseService.withRealm { realm ->
            val pending = realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAll()
            realm.copyFromRealm(pending)
        }
    }

    override fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>): List<String> {
        return databaseService.withRealm { realm ->
            val titles = mutableListOf<String>()
            submissions.forEach { submission ->
                val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()
                exam?.name?.let { titles.add(it) }
            }
            titles
        }
    }

    override fun getUnreadNotificationsSize(userId: String?): Int {
        return databaseService.withRealm { realm ->
            realm.where(RealmNotification::class.java)
                .equalTo("userId", userId)
                .equalTo("isRead", false)
                .count()
                .toInt()
        }
    }
}

// Network Repository
interface NetworkRepository {
    val isNetworkConnectedFlow: kotlinx.coroutines.flow.Flow<Boolean>
    suspend fun isServerReachable(url: String): Boolean
}

class NetworkRepositoryImpl : NetworkRepository {
    override val isNetworkConnectedFlow: kotlinx.coroutines.flow.Flow<Boolean> = org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
    override suspend fun isServerReachable(url: String): Boolean {
        return MainApplication.isServerReachable(url)
    }
}

// Chat Repository
interface ChatRepository {
    val selectedChatHistory: MutableStateFlow<RealmList<Conversation>?>
    val selectedId: MutableStateFlow<String>
    val selectedRev: MutableStateFlow<String>
    val selectedAiProvider: MutableStateFlow<String?>
}

class ChatRepositoryImpl : ChatRepository {
    override val selectedChatHistory: MutableStateFlow<RealmList<Conversation>?> = MutableStateFlow(null)
    override val selectedId: MutableStateFlow<String> = MutableStateFlow("")
    override val selectedRev: MutableStateFlow<String> = MutableStateFlow("")
    override val selectedAiProvider: MutableStateFlow<String?> = MutableStateFlow(null)
}
