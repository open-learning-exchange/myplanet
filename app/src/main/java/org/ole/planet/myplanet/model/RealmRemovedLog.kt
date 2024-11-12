package org.ole.planet.myplanet.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class RealmRemovedLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    private var userId: String? = null
    private var type: String? = null
    private var docId: String? = null

    companion object {
        @JvmStatic
        fun onAdd(mRealm: Realm, type: String?, userId: String?, docId: String?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            mRealm.where(RealmRemovedLog::class.java)
                .equalTo("type", type)
                .equalTo("userId", userId)
                .equalTo("docId", docId)
                .findAll().deleteAllFromRealm()
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun onRemove(mRealm: Realm, type: String, userId: String?, docId: String?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val log = mRealm.createObject(RealmRemovedLog::class.java, UUID.randomUUID().toString())
            log.docId = docId
            log.userId = userId
            log.type = type
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun removedIds(realm: Realm?, type: String, userId: String?): Array<String> {
            val removedLibs = realm?.where(RealmRemovedLog::class.java)
                ?.equalTo("userId", userId)
                ?.equalTo("type", type)
                ?.findAll()

            if (removedLibs != null) {
                val ids = Array(removedLibs.size) { "" }
                for ((i, removed) in removedLibs.withIndex()) {
                    ids[i] = removed.docId ?: ""
                }
                return ids
            }
            return arrayOf()
        }
    }
}
