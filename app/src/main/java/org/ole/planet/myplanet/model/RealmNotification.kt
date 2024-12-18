package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date
import java.util.UUID

class RealmNotification : RealmObject {
    @PrimaryKey
    var id: String = "${UUID.randomUUID()}"
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String = ""
    var relatedId: String? = null
}
