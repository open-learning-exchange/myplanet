package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

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

        fun getMyLifeByUserId(mRealm: Realm, userId: String?): List<RealmMyLife> {
            return mRealm.where(RealmMyLife::class.java).equalTo("userId", userId).findAll()
                .sort("weight")
        }

        fun updateWeight(weight: Int, id: String?, realm: Realm, userId: String?) {
            realm.executeTransaction { mRealm ->
                var currentWeight = -1
                val myLifeList = getMyLifeByUserId(mRealm, userId)
                for (item in myLifeList) {
                    if (id?.let { item._id?.contains(it) } == true) {
                        currentWeight = item.weight
                        item.weight = weight
                    }
                }
                for (item in myLifeList) {
                    if (currentWeight != -1 && item.weight == weight && !id?.let { item._id?.contains(it) }!!) {
                        item.weight = currentWeight
                    }
                }
            }
        }

        fun updateVisibility(isVisible: Boolean, id: String?, realm: Realm, userId: String?) {
            realm.executeTransaction { mRealm ->
                val myLifeList = getMyLifeByUserId(mRealm, userId)
                for (item in myLifeList) {
                    if (id?.let { item._id?.contains(it) } == true) {
                        item.isVisible = isVisible
                    }
                }
            }
        }
    }
}
