package org.ole.planet.myplanet.model

import java.io.File

// App-side java.io.File helpers for MyCourse, kept out of the Room entity.

/** [olePath] is the `<external files>/ole/` directory, trailing slash included (see AppStorage.olePath). */
fun MyCourse.Companion.getCoverImageFile(olePath: String, courseId: String?, fileName: String?): File? {
    if (courseId.isNullOrBlank() || fileName.isNullOrBlank()) return null
    return File(
        "${olePath}course_attachments/$courseId/$fileName"
    )
}
