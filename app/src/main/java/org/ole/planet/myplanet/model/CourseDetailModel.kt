package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.repository.RatingSummary

data class CourseDetailModel(
    val course: MyCourse,
    val user: UserEntity?,
    val ratingSummary: RatingSummary?,
    val examCount: Int,
    val resources: List<MyLibrary>,
    val downloadedResources: List<MyLibrary>,
    val steps: List<StepItem>
)
