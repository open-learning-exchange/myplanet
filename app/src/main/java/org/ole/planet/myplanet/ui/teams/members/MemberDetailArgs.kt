package org.ole.planet.myplanet.ui.teams.members

import org.ole.planet.myplanet.model.UserEntity

/**
 * The member fields [MembersDetailFragment] renders from its arguments. Visit stats are not part of
 * this: the fragment loads those through [MembersDetailViewModel].
 */
data class MemberDetailArgs(
    val id: String?,
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
