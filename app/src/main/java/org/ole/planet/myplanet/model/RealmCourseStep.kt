package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmCourseStep : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var stepTitle: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var noOfResources: Int? = null
}