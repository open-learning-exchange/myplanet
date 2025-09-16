package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmMyLife : RealmObject {
    @PrimaryKey
    var _id: String? = null
    var imageId: String? = null
    var userId: String? = null
    var title: String? = null
    var isVisible = false
    var weight = 0

    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }

    constructor()

}
