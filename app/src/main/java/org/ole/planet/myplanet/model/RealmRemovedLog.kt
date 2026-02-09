package org.ole.planet.myplanet.model

import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.UUID

open class RealmRemovedLog : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var userId: String? = null
    var type: String? = null
    var docId: String? = null

    companion object {
        @JvmStatic
        fun onAdd(mRealm: Realm, type: String?, userId: String?, docId: String?) {
            val startedTransaction = !mRealm.isInTransaction
            if (startedTransaction) {
                mRealm.beginTransaction()
            }
            try {
                mRealm.where(RealmRemovedLog::class.java)
                    .equalTo("type", type)
                    .equalTo("userId", userId)
                    .equalTo("docId", docId)
                    .findAll().deleteAllFromRealm()
                if (startedTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (startedTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
        }

        @JvmStatic
        fun onRemove(mRealm: Realm, type: String, userId: String?, docId: String?) {
            val startedTransaction = !mRealm.isInTransaction
            if (startedTransaction) {
                mRealm.beginTransaction()
            }
            try {
                val log = mRealm.createObject(RealmRemovedLog::class.java, UUID.randomUUID().toString())
                log.docId = docId
                log.userId = userId
                log.type = type
                if (startedTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (startedTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
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
