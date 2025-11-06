package org.ole.planet.myplanet.model

import io.realm.annotations.PrimaryKey
import java.util.Date

data class Notification(
    @PrimaryKey
    var id: String = "",
    var userId: String = "",
    var message: String = "",
    var isRead: Boolean = false,
    var createdAt: Date? = null,
    var type: String = "",
    var relatedId: String? = null,
    var title: String = ""
)
