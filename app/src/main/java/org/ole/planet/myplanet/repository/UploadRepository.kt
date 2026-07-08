package org.ole.planet.myplanet.repository

import io.realm.RealmObject

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadQueryContract<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadUpdateContract<T>, succeeded: List<UploadedItemResult>): List<UploadedItemResult>
}
