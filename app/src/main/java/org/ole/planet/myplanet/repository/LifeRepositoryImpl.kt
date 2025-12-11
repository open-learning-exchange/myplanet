package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @ApplicationContext private val context: Context,
) : RealmRepository(databaseService), LifeRepository {
    override suspend fun getMyLifeList(userId: String?): Flow<List<RealmMyLife>> =
        queryListFlow(RealmMyLife::class.java) {
            equalTo("userId", userId)
            sort("weight")
        }

    override suspend fun setupMyLife(userId: String?) {
        val realmObjects = queryList(RealmMyLife::class.java) {
            equalTo("userId", userId)
        }
        if (realmObjects.isEmpty()) {
            databaseService.executeTransactionAsync { realm ->
                val myLifeListBase = getMyLifeListBase(userId)
                var ml: RealmMyLife
                var weight = 1
                for (item in myLifeListBase) {
                    ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
            }
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, context.getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, context.getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, context.getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, context.getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, context.getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, context.getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, context.getString(R.string.mypersonals)))
        return myLifeList
    }

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        update(RealmMyLife::class.java, "_id", myLifeId) {
            it.isVisible = isVisible
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        databaseService.executeTransactionAsync { realm ->
            list.forEachIndexed { index, myLife ->
                val realmMyLife =
                    realm.where(RealmMyLife::class.java).equalTo("_id", myLife._id).findFirst()
                realmMyLife?.weight = index
            }
        }
    }
}
