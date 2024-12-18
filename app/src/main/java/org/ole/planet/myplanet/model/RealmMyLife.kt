package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.Realm
import android.content.SharedPreferences

class RealmMyLife : RealmObject {
    @PrimaryKey
    var _id: String? = null
    var imageId: String? = null
    var userId: String? = null
    var title: String? = null
    var isVisible: Boolean = false
    var weight: Int = 0

    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }

    constructor()

    companion object {
        fun getMyLifeByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyLife> {
            val userId = settings?.getString("userId", "--")
            return getMyLifeByUserId(realm, userId)
        }

        fun getMyLifeByUserId(realm: Realm, userId: String?): List<RealmMyLife> {
            return realm.query(RealmMyLife::class, "userId == $0", userId).find().sortedBy { it.weight }
        }

        fun updateWeight(weight: Int, id: String?, realm: Realm, userId: String?) {
            realm.writeBlocking {
                val myLifeList = getMyLifeByUserId(realm, userId)
                val targetItem = myLifeList.find { it._id?.contains(id ?: "") == true }

                targetItem?.let { current ->
                    val currentWeight = current.weight
                    current.weight = weight

                    myLifeList.filter { it._id != current._id && it.weight == weight }.forEach {
                        it.weight = currentWeight
                    }
                }
            }
        }

        fun updateVisibility(isVisible: Boolean, id: String?, realm: Realm, userId: String?) {
            realm.writeBlocking {
                val myLifeList = getMyLifeByUserId(realm, userId)
                myLifeList.find { it._id?.contains(id ?: "") == true }?.isVisible = isVisible
            }
        }
    }
}