package org.ole.planet.myplanet.ui.teams.members

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
)
