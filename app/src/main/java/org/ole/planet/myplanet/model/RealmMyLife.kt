package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.concurrent.Executors
import org.ole.planet.myplanet.MainApplication

class RealmMyLife : RealmObject {
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
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.execute {
                    MainApplication.service.withRealm { backgroundRealm ->
                        backgroundRealm.executeTransaction { mRealm ->
                            val targetItem = mRealm.where(RealmMyLife::class.java).equalTo("_id", id)
                                .findFirst()

                            targetItem?.let {
                                val currentWeight = it.weight
                                it.weight = weight

                                val otherItem = mRealm.where(RealmMyLife::class.java)
                                    .equalTo("userId", userId).equalTo("weight", weight)
                                    .notEqualTo("_id", id).findFirst()

                                otherItem?.weight = currentWeight
                            }
                        }
                    }
                }
            } finally {
                executor.shutdown()
            }
        }

        @JvmStatic
        fun updateVisibility(isVisible: Boolean, id: String?) {
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.execute {
                    MainApplication.service.withRealm { backgroundRealm ->
                        backgroundRealm.executeTransaction { mRealm ->
                            mRealm.where(RealmMyLife::class.java).equalTo("_id", id).findFirst()
                                ?.isVisible = isVisible
                        }
                    }
                }
            } finally {
                executor.shutdown()
            }
        }
    }
}
