package org.ole.planet.myplanet.ui.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Utility object that centralizes fragment navigation.
 * Use [replaceFragment] instead of direct fragment transactions to ensure
 * consistent navigation behavior across the app.
 */
object NavigationHelper {
    /**
     * Replaces a fragment in the specified container.
     *
     * @param fragmentManager manager used to execute the transaction
     * @param containerId id of the container where the fragment will be placed
     * @param fragment fragment instance to display
     * @param addToBackStack whether the transaction should be added to the back stack
     * @param tag optional tag for the fragment and back stack entry
     * @param allowStateLoss whether to allow committing state loss
     */
    fun replaceFragment(
        fragmentManager: FragmentManager,
        containerId: Int,
        fragment: Fragment,
        addToBackStack: Boolean = false,
        tag: String? = null,
        allowStateLoss: Boolean = false
    ) {
        fragmentManager.beginTransaction().apply {
            replace(containerId, fragment, tag)
            if (addToBackStack) {
                addToBackStack(tag)
            }
            if (allowStateLoss) {
                commitAllowingStateLoss()
            } else {
                commit()
            }
        }
    }

    /**
     * Pops the back stack of the provided [fragmentManager] if there are entries.
     *
     * @param fragmentManager manager whose back stack will be popped
     * @param tag optional tag identifying the entry to pop to
     * @param flags optional flags to control popping behavior
     */
    fun popBackStack(
        fragmentManager: FragmentManager,
        tag: String? = null,
        flags: Int = 0
    ) {
        if (fragmentManager.backStackEntryCount > 0) {
            if (tag == null && flags == 0) {
                fragmentManager.popBackStack()
            } else {
                fragmentManager.popBackStack(tag, flags)
            }
        }
    }
}
