package org.ole.planet.myplanet.model

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmSubmission : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var parentId: String? = null
    var type: String? = null
    var userId: String? = null
    var user: String? = null
    var startTime: Long = 0
    var lastUpdateTime: Long = 0
    var answers: RealmList<RealmAnswer>? = null
    var team: String? = null
    var grade: Long = 0
    var status: String? = null
    var uploaded = false
    var sender: String? = null
    var source: String? = null
    var parentCode: String? = null
    var parent: String? = null
    var membershipDoc: RealmMembershipDoc? = null
    var isUpdated = false

    companion object {}
}

open class RealmMembershipDoc : RealmObject() {
    var teamId: String? = null
}
