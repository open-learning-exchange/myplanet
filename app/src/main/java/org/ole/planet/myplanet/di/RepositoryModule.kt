package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import io.realm.Sort
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

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
    fun provideNewsRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): NewsRepository {
        return NewsRepositoryImpl(databaseService.realmInstance, apiInterface)
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

// News Repository
interface NewsRepository {
    fun getRealm(): Realm
    fun getTopLevelNewsAsync(): RealmResults<RealmNews>
    fun getTopLevelNewsList(): List<RealmNews>
    fun getNewsById(id: String?): RealmNews?
    fun getReplies(parentId: String?): List<RealmNews>
    fun createNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
        imageUrls: RealmList<String>?,
        isReply: Boolean = false
    ): RealmNews
    fun addAttachment(newsId: String, attachment: String)
}

class NewsRepositoryImpl(
    private val realm: Realm,
    private val apiInterface: ApiInterface
) : NewsRepository {
    override fun getRealm(): Realm = realm

    override fun getTopLevelNewsAsync(): RealmResults<RealmNews> {
        return realm.where(RealmNews::class.java)
            .sort("time", Sort.DESCENDING)
            .isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE)
            .findAllAsync()
    }

    override fun getTopLevelNewsList(): List<RealmNews> {
        return realm.where(RealmNews::class.java)
            .isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE)
            .findAll()
    }

    override fun getNewsById(id: String?): RealmNews? {
        return realm.where(RealmNews::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getReplies(parentId: String?): List<RealmNews> {
        return realm.where(RealmNews::class.java)
            .sort("time", Sort.DESCENDING)
            .equalTo("replyTo", parentId, Case.INSENSITIVE)
            .findAll()
    }

    override fun createNews(
        map: HashMap<String?, String>,
        user: RealmUserModel?,
        imageUrls: RealmList<String>?,
        isReply: Boolean
    ): RealmNews {
        return RealmNews.createNews(map, realm, user, imageUrls, isReply)
    }

    override fun addAttachment(newsId: String, attachment: String) {
        realm.executeTransaction {
            val news = it.where(RealmNews::class.java)
                .equalTo("id", newsId)
                .findFirst()
            if (news != null) {
                if (news.imageUrls == null) news.imageUrls = RealmList()
                news.imageUrls?.add(attachment)
            }
        }
    }
}
