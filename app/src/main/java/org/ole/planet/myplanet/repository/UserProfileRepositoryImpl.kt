package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.RealmList
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

import org.ole.planet.myplanet.di.AppPreferences

class UserProfileRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences,
    @ApplicationContext private val context: Context
) : UserProfileRepository {

    private var mRealm: Realm = databaseService.realmInstance
    private var user: RealmUserModel? = null
    private var handler: UserProfileDbHandler = UserProfileDbHandler(context)

    init {
        val userId = sharedPreferences.getString("userId", "")
        user = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
    }

    override fun getProfile(): RealmUserModel? {
        return user
    }

    override fun getAchievements(): List<RealmAchievement> {
        return mRealm.where(RealmAchievement::class.java).findAll()
    }

    override fun getTeams(): List<RealmMyTeam> {
        val userId = sharedPreferences.getString("userId", "")
        return mRealm.where(RealmMyTeam::class.java).equalTo("userId", userId).findAll()
    }

    override fun getOtherInfo(): RealmList<String> {
        return user?.otherUserProfiles ?: RealmList()
    }

    override fun getStats(): LinkedHashMap<String, String?> {
        return linkedMapOf(
            context.getString(R.string.community_name) to Utilities.checkNA(user?.planetCode),
            context.getString(R.string.last_login) to handler.lastVisit?.let { Utilities.getRelativeTime(it) },
            context.getString(R.string.total_visits_overall) to handler.offlineVisits.toString(),
            context.getString(R.string.most_opened_resource) to Utilities.checkNA(handler.maxOpenedResource),
            context.getString(R.string.number_of_resources_opened) to Utilities.checkNA(handler.numberOfResourceOpen)
        )
    }

    override fun updateProfile(user: RealmUserModel) {
        mRealm.executeTransactionAsync { realm ->
            realm.copyToRealmOrUpdate(user)
        }
    }
}
