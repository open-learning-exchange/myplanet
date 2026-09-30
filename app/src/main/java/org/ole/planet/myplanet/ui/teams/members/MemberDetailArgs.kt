package org.ole.planet.myplanet.ui.teams.members

import org.ole.planet.myplanet.model.UserEntity

/**
 * The member fields [MembersDetailFragment] renders from its arguments. Visit stats are not part of
 * this: the fragment loads those through [MembersDetailViewModel].
 */
data class MemberDetailArgs(
    val id: String?,
    /**
     * The CouchDB login name, e.g. "john_doe". This is the key `offline_activity.userName` is
     * written with, so the visit-stats lookup needs it -- [username] is a display name and will
     * not match any row.
     */
    val loginName: String?,
    val name: String,
    val email: String,
    val dob: String,
    val language: String,
    val phone: String,
    val username: String,
    val memberLevel: String,
    val imageUrl: String?
) {
    companion object {
        /**
         * The mapping shared by the team members list and the voices screens. The community
         * leaders list builds its own arguments, so it is deliberately not routed through here.
         */
        fun fromUser(user: UserEntity): MemberDetailArgs {
            val fullName = "${user.firstName} ${user.lastName}"
            return MemberDetailArgs(
                id = user.id,
                loginName = user.name,
                name = fullName.trim().ifBlank { user.name }.toString(),
                email = user.email.toString(),
                dob = user.dob.toString().substringBefore("T"),
                language = user.language.toString(),
                phone = user.phoneNumber.toString(),
                username = fullName,
                memberLevel = user.level.toString(),
                imageUrl = user.userImage
            )
        }
    }
}
