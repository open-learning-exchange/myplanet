package org.ole.planet.myplanet.ui.teams

import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.callback.OnTeamPageListener

const val FRAGMENT_TYPE_KEY = "fragmentType"

fun resolveTeamPageListener(fragments: List<Fragment>, targetPageId: String): OnTeamPageListener? {
    return fragments.firstOrNull {
        it is OnTeamPageListener && it.arguments?.getString(FRAGMENT_TYPE_KEY) == targetPageId
    } as? OnTeamPageListener
}
