package org.ole.planet.myplanet.service

import android.content.Context
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

/**
 * Provides the current logged in [RealmUserModel].
 * Must be initialised once with application context.
 */
object UserSession {
    private var handler: UserProfileDbHandler? = null

    fun init(context: Context) {
        handler = UserProfileDbHandler(context.applicationContext)
    }

    val user: RealmUserModel?
        get() = handler?.userModel
}
