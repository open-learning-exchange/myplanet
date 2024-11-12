package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmCourseStep : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var courseId: String? = null
    var stepTitle: String? = null
    var description: String? = null
    var noOfResources: Int? = null
}