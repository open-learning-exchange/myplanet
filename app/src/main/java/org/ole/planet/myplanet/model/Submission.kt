package org.ole.planet.myplanet.model

open class Submission {
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var parentId: String? = null
    var type: String? = null
    var userId: String? = null
    var user: String? = null
    var startTime: Long = 0
    var lastUpdateTime: Long = 0
    var answers: MutableList<Answer>? = null
    var teamObject: TeamReference? = null
    var grade: Long = 0
    var status: String? = null
    var uploaded = false
    var sender: String? = null
    var source: String? = null
    var parentCode: String? = null
    var parent: String? = null
    var membershipDoc: MembershipDoc? = null
    var isUpdated = false
    @Transient
    var submitterName: String = ""
}

open class MembershipDoc {
    var teamId: String? = null
}

open class TeamReference {
    var _id: String? = null
    var name: String? = null
    var type: String? = null
}
