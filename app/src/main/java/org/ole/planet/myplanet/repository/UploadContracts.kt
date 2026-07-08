package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import kotlin.reflect.KClass

data class UploadQueryContract<T : RealmObject>(
    val modelClass: KClass<T>,
    val queryBuilder: (RealmQuery<T>) -> RealmQuery<T>
)

data class UploadUpdateContract<T : RealmObject>(
    val modelClass: KClass<T>,
    val idExtractor: (T) -> String?,
    val additionalUpdates: ((Realm, T, UploadedItemResult) -> Unit)? = null
)

data class UploadedItemResult(
    val localId: String,
    val remoteId: String,
    val remoteRev: String
)
