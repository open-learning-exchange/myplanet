package org.ole.planet.myplanet.model

data class JoinedMemberData(
    val user: UserEntity,
    val visitCount: Long,
    val lastVisitDate: Long?,
    val offlineVisits: String,
    val profileLastVisit: String,
    var isLeader: Boolean
)
