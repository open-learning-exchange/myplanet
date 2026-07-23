package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room replacement for the former Realm `MyLife` model.
 *
 * The class name is kept so the UI (which uses it purely as a detached data holder) is unaffected
 * by the migration. Persistence now goes through [org.ole.planet.myplanet.data.room.dao.MyLifeDao].
 */
@Entity(tableName = "my_life", indices = [Index("userId")])
class MyLife {
    @PrimaryKey
    var _id: String = ""
    var imageId: String? = null
    var userId: String? = null
    var title: String? = null
    var isVisible: Boolean = false
    var weight: Int = 0

    constructor()

    @Ignore
    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }
}
