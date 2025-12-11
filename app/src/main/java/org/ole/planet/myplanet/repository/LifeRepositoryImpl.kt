package org.ole.planet.myplanet.repository

import javax.inject.Inject
import android.content.SharedPreferences
import java.util.UUID
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.ui.dashboard.MyLife

class LifeRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val settings: SharedPreferences
) : LifeRepository {
    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        databaseService.executeTransactionAsync { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
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

    override suspend fun setUpMyLife(userId: String?) {
        databaseService.executeTransactionAsync { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
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

    private fun getMyLifeListBase(userId: String?): List<MyLife> {
        return listOf(
            MyLife(
                "ic_myhealth",
                R.drawable.ic_myhealth,
                userId,
                "MyHealth"
            ),
            MyLife(
                "ic_submissions",
                R.drawable.ic_submissions,
                userId,
                "Submissions"
            ),
            MyLife(
                "ic_achievements",
                R.drawable.ic_achievements,
                userId,
                "Achievements"
            ),
            MyLife(
                "ic_examinations",
                R.drawable.ic_examinations,
                userId,
                "Examinations"
            ),
            MyLife(
                "ic_calendar",
                R.drawable.ic_calendar,
                userId,
                "Calendar"
            ),
            MyLife(
                "ic_help",
                R.drawable.ic_help,
                userId,
                "Help"
            ),
            MyLife(
                "ic_feedback",
                R.drawable.ic_feedback,
                userId,
                "Feedback"
            ),
            MyLife(
                "ic_logout",
                R.drawable.ic_logout,
                userId,
                "Logout"
            )
        )
    }
}
