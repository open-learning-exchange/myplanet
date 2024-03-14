package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmMyLife : RealmObject {
    @PrimaryKey
    private var _id: String? = null
    @JvmField
    var imageId: String? = null
    @JvmField
    var userId: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var isVisible = false
    @JvmField
    var weight = 0

    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }

    constructor()

    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
    }

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

        fun createMyLife(myLife: RealmMyLife, mRealm: Realm, _id: String?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            myLife.set_id(_id)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun updateWeight(weight: Int, _id: String?, realm: Realm, userId: String?) {
            realm.executeTransaction { realm ->
                var currentWeight = -1
                val myLifeList = getMyLifeByUserId(realm, userId)
                for (item in myLifeList) {
                    if (item.get_id()!!.contains(_id!!)) {
                        currentWeight = item.weight
                        item.weight = weight
                    }
                }
                for (item in myLifeList) {
                    if (currentWeight != -1 && item.weight == weight && !item.get_id()!!.contains(_id!!)) {
                        item.weight = currentWeight
                    }
                }
            }
        }

        @JvmStatic
        fun updateVisibility(isVisible: Boolean, _id: String?, realm: Realm, userId: String?) {
            realm.executeTransaction { realm ->
                val myLifeList = getMyLifeByUserId(realm, userId)
                for (item in myLifeList) {
                    if (item.get_id()!!.contains(_id!!)) {
                        item.isVisible = isVisible
                    }
                }
            }
        }
    }
}
