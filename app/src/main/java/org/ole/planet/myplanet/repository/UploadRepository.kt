package org.ole.planet.myplanet.repository

import io.realm.RealmObject
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadConfig<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadConfig<T>, succeeded: List<UploadedItem>)
}
