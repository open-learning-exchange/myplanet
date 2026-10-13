package org.ole.planet.myplanet.model

data class JoinedMemberData(
    val user: UserEntity,
    val visitCount: Long,
    val lastVisitDate: Long?,
    var isLeader: Boolean
)
