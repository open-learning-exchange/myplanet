package org.ole.planet.myplanet.ui.community

sealed class CommunityServicesRoute {
    data class ExternalLink(val url: String) : CommunityServicesRoute()
    data class TeamLink(val teamId: String) : CommunityServicesRoute()
    data object Unhandled : CommunityServicesRoute()

    companion object {
        fun resolve(route: String): CommunityServicesRoute {
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
