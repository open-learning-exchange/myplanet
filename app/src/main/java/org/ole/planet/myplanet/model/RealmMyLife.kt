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
        @Deprecated("Use LifeRepository.getMyLifeByUserId instead")
        fun getMyLifeByUserId(mRealm: Realm, settings: SharedPreferences?): List<RealmMyLife> {
            val userId = settings?.getString("userId", "--")
            return getMyLifeByUserId(mRealm, userId)
        }

        @Deprecated("Use LifeRepository.getMyLifeByUserId instead")
        @JvmStatic
        fun getMyLifeByUserId(mRealm: Realm, userId: String?): List<RealmMyLife> {
            return mRealm.where(RealmMyLife::class.java).equalTo("userId", userId).findAll()
                .sort("weight")
        }


        @JvmStatic
        fun updateVisibility(isVisible: Boolean, id: String?) {
            MainApplication.applicationScope.launch {
                val databaseService = (MainApplication.context as MainApplication).databaseService
                databaseService.executeTransactionAsync { realm ->
                    realm.where(RealmMyLife::class.java)
                        .equalTo("_id", id)
                        .findFirst()
                        ?.isVisible = isVisible
                }
            }
        }
    }
}
