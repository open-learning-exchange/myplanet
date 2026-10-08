package org.ole.planet.myplanet.model


data class HealthRecord(
    val healthPojo: HealthExamination,
    val healthProfile: MyHealth,
    val examinations: List<HealthExamination>,
    val userMap: Map<String, UserEntity>
)
