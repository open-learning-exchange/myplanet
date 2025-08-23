package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.realm.Case
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter

interface MyHealthRepository {
    suspend fun getUserModel(userId: String?): RealmUserModel?
    suspend fun getAllUsers(sortBy: String, sort: Sort): List<RealmUserModel>
    suspend fun searchUsers(query: String): List<RealmUserModel>
    suspend fun getMyHealthPojo(userId: String?): RealmMyHealthPojo?
    suspend fun getMyHealthProfile(mh: RealmMyHealthPojo, userModel: RealmUserModel?): RealmMyHealth?
    suspend fun getExaminations(userKey: String?): List<RealmMyHealthPojo>?
}

class MyHealthRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RealmRepository(databaseService), MyHealthRepository {

    override suspend fun getUserModel(userId: String?): RealmUserModel? = withRealm { realm ->
        realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
    }

    override suspend fun getAllUsers(sortBy: String, sort: Sort): List<RealmUserModel> = withRealm { realm ->
        realm.where(RealmUserModel::class.java).sort(sortBy, sort).findAll()
    }

    override suspend fun searchUsers(query: String): List<RealmUserModel> = withRealm { realm ->
        realm.where(RealmUserModel::class.java)
            .contains("firstName", query, Case.INSENSITIVE)
            .or()
            .contains("lastName", query, Case.INSENSITIVE)
            .or()
            .contains("name", query, Case.INSENSITIVE)
            .sort("joinDate", Sort.DESCENDING)
            .findAll()
    }

    override suspend fun getMyHealthPojo(userId: String?): RealmMyHealthPojo? = withRealm { realm ->
        var mh = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        mh
    }

    override suspend fun getMyHealthProfile(mh: RealmMyHealthPojo, userModel: RealmUserModel?): RealmMyHealth? {
        val json = AndroidDecrypter.decrypt(mh.data, userModel?.key, userModel?.iv)
        return if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                Gson().fromJson(json, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    override suspend fun getExaminations(userKey: String?): List<RealmMyHealthPojo>? = withRealm { realm ->
        realm.where(RealmMyHealthPojo::class.java).equalTo("profileId", userKey).findAll()
    }
}
