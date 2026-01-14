package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), HealthRepository {
    override suspend fun getHealthData(userId: String, currentUser: RealmUserModel): HealthRecord? = withRealm { realm ->
        var mh = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh == null) return@withRealm null

        val json = AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (android.text.TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                JsonUtils.gson.fromJson(json, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (mm == null) return@withRealm null

        val healths = realm.where(RealmHealthExamination::class.java).equalTo("profileId", mm.userKey).findAll()
        val list = realm.copyFromRealm(healths)
        if (list.isEmpty()) {
            return@withRealm HealthRecord(mh, mm, emptyList(), emptyMap())
        }

        val userIds = list.mapNotNull {
            it.getEncryptedDataAsJson(currentUser).let { jsonData ->
                jsonData.get("createdBy")?.asString
            }
        }.distinct()

        val userMap = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            val users = realm.where(RealmUserModel::class.java).`in`("id", userIds.toTypedArray()).findAll()
            realm.copyFromRealm(users).filter { it.id != null }.associateBy { it.id!! }
        }
        HealthRecord(mh, mm, list, userMap)
    }
}
