package org.ole.planet.myplanet.model
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "submissions", indices = [Index("_id"), Index("_rev"), Index("parentId"), Index("type"), Index("userId"), Index("isUpdated")])
open class Submission(
    @PrimaryKey @JvmField var id: String = "",
    @JvmField var _id: String? = null,
    @JvmField var _rev: String? = null,
    var parentId: String? = null,
    var type: String? = null,
    var userId: String? = null,
    var user: String? = null,
    var startTime: Long = 0,
    var lastUpdateTime: Long = 0,
    @Ignore var answers: MutableList<Answer>? = null,
    @Ignore var teamObject: TeamReference? = null,
    var grade: Long = 0,
    var status: String? = null,
    var uploaded: Boolean = false,
    var sender: String? = null,
    var source: String? = null,
    var parentCode: String? = null,
    var parent: String? = null,
    @Ignore var membershipDoc: MembershipDoc? = null,
    var teamId: String? = null,
    var isUpdated: Boolean = false
) {
    @Ignore
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
