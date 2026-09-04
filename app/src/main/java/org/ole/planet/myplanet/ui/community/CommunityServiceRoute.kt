package org.ole.planet.myplanet.ui.community

sealed class CommunityServiceRoute {
    data class ExternalLink(val url: String) : CommunityServiceRoute()
    data class TeamLink(val teamId: String) : CommunityServiceRoute()
    data object Unhandled : CommunityServiceRoute()

    companion object {
        fun resolve(route: String): CommunityServiceRoute {
            if (route.startsWith("http://") || route.startsWith("https://")) {
                return ExternalLink(route)
            }
            val segments = route.split("/")
            return if (segments.size >= 4) {
                TeamLink(segments[3])
            } else {
                Unhandled
            }
        }
    }
}
