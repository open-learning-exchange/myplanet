package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "submit_photos", indices = [androidx.room.Index("uploaded")])
open class SubmitPhotos {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var submissionId: String? = null
    var courseId: String? = null
    var examId: String? = null
    var memberId: String? = null
    var date: String? = null
    var uniqueId: String? = null
    var photoLocation: String? = null
    var uploaded = false

    companion object
}
