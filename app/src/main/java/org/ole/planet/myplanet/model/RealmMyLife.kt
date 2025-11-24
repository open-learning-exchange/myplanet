package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication

open class RealmMyLife : RealmObject {
    @PrimaryKey
    var _id: String? = null
    var imageId: String? = null
    var userId: String? = null
    var title: String? = null
    var isVisible = false
    var weight = 0

    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }

    constructor()

    companion object {
        fun getMyLifeByUserId(mRealm: Realm, settings: SharedPreferences?): List<RealmMyLife> {
            val userId = settings?.getString("userId", "--")
            return getMyLifeByUserId(mRealm, userId)
        }

        @JvmStatic
        fun getMyLifeByUserId(mRealm: Realm, userId: String?): List<RealmMyLife> {
            return mRealm.where(RealmMyLife::class.java).equalTo("userId", userId).findAll()
                .sort("weight")
        }

        @JvmStatic
        fun updateWeight(weight: Int, id: String?, userId: String?) {
            MainApplication.applicationScope.launch {
                MainApplication.service.executeTransactionAsync { realm ->
                    val targetItem = realm.where(RealmMyLife::class.java)
                        .equalTo("_id", id)
                        .findFirst()

                    targetItem?.let { item ->
                        val currentWeight = item.weight
                        item.weight = weight

                        val otherItem = realm.where(RealmMyLife::class.java)
                            .equalTo("userId", userId)
                            .equalTo("weight", weight)
                            .notEqualTo("_id", id)
                            .findFirst()

                        otherItem?.weight = currentWeight
                    }
                }
            }
        }

        @JvmStatic
        fun updateVisibility(isVisible: Boolean, id: String?) {
            MainApplication.applicationScope.launch {
                MainApplication.service.executeTransactionAsync { realm ->
                    realm.where(RealmMyLife::class.java)
                        .equalTo("_id", id)
                        .findFirst()
                        ?.isVisible = isVisible
                }
            }
        }
    }
}
