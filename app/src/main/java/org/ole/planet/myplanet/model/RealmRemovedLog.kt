package org.ole.planet.myplanet.model

import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.ext.query

class RealmRemovedLog : RealmObject {
    @PrimaryKey
    var id: String? = null
    var userId: String? = null
    var type: String? = null
    var docId: String? = null

    companion object {
        fun onAdd(realm: Realm, type: String?, userId: String?, docId: String?) {
            realm.writeBlocking {
                query<RealmRemovedLog>("type == $0 AND userId == $1 AND docId == $2",
                    type ?: "",
                    userId ?: "",
                    docId ?: ""
                ).find()
                    .forEach { delete(it) }
            }
        }

        fun onRemove(realm: Realm, type: String, userId: String?, docId: String?) {
            realm.writeBlocking {
                copyToRealm(RealmRemovedLog().apply {
                    this.docId = docId
                    this.userId = userId
                    this.type = type
                })
            }
        }

        fun removedIds(realm: Realm, type: String, userId: String?): Array<String> {
            val removedLibs = realm.query<RealmRemovedLog>("userId == $0 AND type == $1", userId, type).find()

            return removedLibs.mapNotNull { it.docId }.toTypedArray()
        }
    }
}
