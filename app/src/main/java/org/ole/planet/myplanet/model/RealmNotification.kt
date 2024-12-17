package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Date
import java.util.UUID

open class RealmNotification : RealmObject() {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var userId: String = ""
    var message: String = ""
    var isRead: Boolean = false
    var createdAt: Date = Date()
    var type: String = ""
    var relatedId: String? = null
    var lang: String? = null
}
