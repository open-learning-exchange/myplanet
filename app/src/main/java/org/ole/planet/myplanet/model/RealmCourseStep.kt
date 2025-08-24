package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmCourseStep : RealmObject {
    @PrimaryKey
    var id: String? = null
    var courseId: String? = null
    var stepTitle: String? = null
    var description: String? = null
    var noOfResources: Int? = null
}
