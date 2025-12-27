package org.ole.planet.myplanet.utilities

import android.util.Patterns

object ValidationUtils {
    fun isValidEmail(target: CharSequence): Boolean {
        return target.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }
}
