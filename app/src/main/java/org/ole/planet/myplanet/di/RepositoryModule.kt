package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyCourse
import javax.inject.Singleton

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
    fun provideSyncRepository(
        databaseService: DatabaseService,
        @AppPreferences settings: SharedPreferences,
        @DefaultPreferences defaultPreferences: SharedPreferences
    ): SyncRepository {
        return SyncRepositoryImpl(databaseService, settings, defaultPreferences)
    }

    @Provides
    @Singleton
    fun provideDashboardRepository(
        databaseService: DatabaseService,
        @AppPreferences settings: SharedPreferences
    ): DashboardRepository {
        return DashboardRepositoryImpl(databaseService, settings)
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

// Sync Repository
interface SyncRepository {
    suspend fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): RealmUserModel?
    suspend fun isAutoSyncEnabled(): Boolean
    suspend fun getAutoSyncPosition(): Int
}

class SyncRepositoryImpl(
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    @DefaultPreferences private val defaultPreferences: SharedPreferences
) : SyncRepository {
    override suspend fun authenticateUser(
        username: String?,
        password: String?,
        isManagerMode: Boolean
    ): RealmUserModel? = withContext(Dispatchers.IO) {
        val realm = databaseService.realmInstance
        val user = realm.where(RealmUserModel::class.java)
            .equalTo("name", username)
            .findFirst()
        user?.let {
            if (it._id?.isEmpty() == true) {
                if (username == it.name && password == it.password) it else null
            } else {
                if (org.ole.planet.myplanet.utilities.AndroidDecrypter.androidDecrypter(
                    username,
                    password,
                    it.derived_key,
                    it.salt
                ) && (!isManagerMode || it.isManager())) {
                    it
                } else null
            }
        }
    }

    override suspend fun isAutoSyncEnabled(): Boolean = withContext(Dispatchers.IO) {
        settings.getBoolean("autoSync", true)
    }

    override suspend fun getAutoSyncPosition(): Int = withContext(Dispatchers.IO) {
        settings.getInt("autoSyncPosition", 0)
    }
}

// Dashboard Repository
interface DashboardRepository {
    suspend fun updateResourceNotification(userId: String?)
    suspend fun getUnreadNotificationsSize(userId: String?): Int
    suspend fun getPendingSurveyTitles(userId: String?): List<String>
    suspend fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?)
    suspend fun getMyLibraryByUser(): List<RealmMyLibrary>
}

class DashboardRepositoryImpl(
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences
) : DashboardRepository {
    override suspend fun updateResourceNotification(userId: String?) {
        withContext(Dispatchers.IO) {
            val realm = databaseService.realmInstance
            realm.executeTransaction { r ->
                val resourceCount = org.ole.planet.myplanet.base.BaseResourceFragment.getLibraryList(r, userId).size
                val existing = r.where(org.ole.planet.myplanet.model.RealmNotification::class.java)
                    .equalTo("userId", userId)
                    .equalTo("type", "resource")
                    .findFirst()
                if (resourceCount > 0) {
                    if (existing != null) {
                        existing.message = "$resourceCount"
                        existing.relatedId = "$resourceCount"
                    } else {
                        createNotificationIfNotExists(r, "resource", "$resourceCount", "$resourceCount", userId)
                    }
                } else {
                    existing?.deleteFromRealm()
                }
            }
        }
    }

    private fun createNotificationIfNotExists(
        realm: Realm,
        type: String,
        message: String,
        relatedId: String?,
        userId: String?
    ) {
        val existing = realm.where(org.ole.planet.myplanet.model.RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("type", type)
            .equalTo("relatedId", relatedId)
            .findFirst()
        if (existing == null) {
            realm.createObject(org.ole.planet.myplanet.model.RealmNotification::class.java, java.util.UUID.randomUUID().toString()).apply {
                this.userId = userId ?: ""
                this.type = type
                this.message = message
                this.relatedId = relatedId
                this.createdAt = java.util.Date()
            }
        }
    }

    override suspend fun getUnreadNotificationsSize(userId: String?): Int = withContext(Dispatchers.IO) {
        val realm = databaseService.realmInstance
        realm.where(org.ole.planet.myplanet.model.RealmNotification::class.java)
            .equalTo("userId", userId)
            .equalTo("isRead", false)
            .count()
            .toInt()
    }

    override suspend fun getPendingSurveyTitles(userId: String?): List<String> = withContext(Dispatchers.IO) {
        val realm = databaseService.realmInstance
        val submissions = realm.where(org.ole.planet.myplanet.model.RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", io.realm.Case.INSENSITIVE)
            .findAll()
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val examId = submission.parentId?.split("@")?.firstOrNull() ?: ""
            val exam = realm.where(org.ole.planet.myplanet.model.RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        titles
    }

    override suspend fun getMyLibraryByUser(): List<RealmMyLibrary> = withContext(Dispatchers.IO) {
        val realm = databaseService.realmInstance
        RealmMyLibrary.getMyLibraryByUserId(realm, settings)
    }

    override suspend fun createNotificationIfNotExists(type: String, message: String, relatedId: String?, userId: String?) {
        withContext(Dispatchers.IO) {
            val realm = databaseService.realmInstance
            realm.executeTransaction { r ->
                createNotificationIfNotExists(r, type, message, relatedId, userId)
            }
        }
    }
}