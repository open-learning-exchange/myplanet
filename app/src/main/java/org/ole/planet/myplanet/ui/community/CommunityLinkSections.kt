package org.ole.planet.myplanet.ui.community

import androidx.annotation.DrawableRes
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyTeam

data class CommunityLinkSections(val local: List<MyTeam>, val social: List<MyTeam>)

fun List<MyTeam>.toLinkSections(): CommunityLinkSections {
    val (social, local) = partition { it.teamType == SOCIAL_TEAM_TYPE }
    return CommunityLinkSections(local, social)
}

@DrawableRes
fun MyTeam.linkIconRes(): Int = when (icon) {
    "groups", "group", "people" -> R.drawable.ic_link_team
    "work" -> R.drawable.ic_link_enterprise
    "instagram" -> R.drawable.ic_link_instagram
    "facebook" -> R.drawable.ic_link_facebook
    "whatsapp" -> R.drawable.ic_link_whatsapp
    "discord" -> R.drawable.ic_link_discord
    "x" -> R.drawable.ic_link_x
    "youtube" -> R.drawable.ic_link_youtube
    "tiktok" -> R.drawable.ic_link_tiktok
    "web", "website" -> R.drawable.ic_link_web
    else -> if (teamType == SOCIAL_TEAM_TYPE) R.drawable.ic_link_web else R.drawable.ic_link
}

private const val SOCIAL_TEAM_TYPE = "social"
