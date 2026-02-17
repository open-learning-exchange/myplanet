package org.ole.planet.myplanet.model

import io.realm.RealmList

fun RealmMyTeam.toDto(): Team {
    return Team(
        _id = this._id ?: "",
        _rev = this._rev,
        name = this.name,
        title = this.title,
        description = this.description,
        limit = this.limit,
        status = this.status,
        teamId = this.teamId,
        teamType = this.teamType,
        type = this.type,
        services = this.services,
        rules = this.rules,
        parentCode = this.parentCode,
        createdBy = this.createdBy,
        userPlanetCode = this.userPlanetCode,
        teamPlanetCode = this.teamPlanetCode,
        isLeader = this.isLeader,
        isPublic = this.isPublic,
        amount = this.amount,
        createdDate = this.createdDate,
        updatedDate = this.updatedDate,
        requests = this.requests,
        courses = this.courses?.toList(),
        route = this.route
    )
}

fun RealmUser.toMemberDto(): Member {
    return Member(
        id = this.id,
        _id = this._id,
        name = this.name,
        firstName = this.firstName,
        lastName = this.lastName,
        middleName = this.middleName,
        email = this.email,
        phoneNumber = this.phoneNumber,
        joinDate = this.joinDate,
        userImage = this.userImage,
        dob = this.dob,
        language = this.language,
        level = this.level,
        roles = this.rolesList?.filterNotNull(),
        planetCode = this.planetCode,
        parentCode = this.parentCode
    )
}

fun Member.toRealmUser(): RealmUser {
    val user = RealmUser()
    user.id = this.id
    user._id = this._id
    user.name = this.name
    user.firstName = this.firstName
    user.lastName = this.lastName
    user.middleName = this.middleName
    user.email = this.email
    user.phoneNumber = this.phoneNumber
    user.joinDate = this.joinDate
    user.userImage = this.userImage
    user.dob = this.dob
    user.language = this.language
    user.level = this.level
    user.planetCode = this.planetCode
    user.parentCode = this.parentCode
    user.rolesList = RealmList()
    this.roles?.forEach { user.rolesList?.add(it) }
    return user
}
