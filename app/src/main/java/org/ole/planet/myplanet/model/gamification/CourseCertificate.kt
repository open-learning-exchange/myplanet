package org.ole.planet.myplanet.model.gamification

data class CourseCertificate(
    val courseId: String,
    val courseTitle: String,
    val learnerName: String,
    val completionDate: String,
    val certificateId: String,
    val organization: String = "Open Learning Exchange"
)
