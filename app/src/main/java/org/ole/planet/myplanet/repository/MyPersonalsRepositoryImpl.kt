package org.ole.planet.myplanet.repository

import javax.inject.Inject
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalsRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : MyPersonalsRepository {
    override fun getMyPersonals(userId: String): List<RealmMyPersonal> {
        val mRealm = databaseService.realmInstance
        return mRealm.where(RealmMyPersonal::class.java).equalTo("userId", userId).findAll()
    }

    override fun deletePersonal(personalId: String) {
        val mRealm = databaseService.realmInstance
        mRealm.executeTransaction {
            it.where(RealmMyPersonal::class.java).equalTo("_id", personalId).findFirst()
                ?.deleteFromRealm()
        }
    }

    override fun updatePersonal(personal: RealmMyPersonal) {
        val mRealm = databaseService.realmInstance
        mRealm.executeTransaction {
            it.copyToRealmOrUpdate(personal)
        }
    }
}
