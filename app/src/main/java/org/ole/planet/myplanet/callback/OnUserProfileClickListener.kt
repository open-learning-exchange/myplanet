package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.User

interface OnUserProfileClickListener {
    fun onItemClick(user: User)
}
