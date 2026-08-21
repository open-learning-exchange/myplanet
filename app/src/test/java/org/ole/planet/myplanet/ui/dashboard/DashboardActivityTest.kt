package org.ole.planet.myplanet.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardActivityTest {

    @Test
    fun isSyncBannerVisible_inPortraitWhenNotDismissed_returnsTrue() {
        val isVisible = DashboardActivity.isSyncBannerVisible(isLandscape = false, isDismissed = false)
        assertTrue(isVisible)
    }

    @Test
    fun isSyncBannerVisible_inPortraitWhenDismissed_returnsFalse() {
        val isVisible = DashboardActivity.isSyncBannerVisible(isLandscape = false, isDismissed = true)
        assertFalse(isVisible)
    }

    @Test
    fun isSyncBannerVisible_inLandscapeWhenNotDismissed_returnsFalse() {
        val isVisible = DashboardActivity.isSyncBannerVisible(isLandscape = true, isDismissed = false)
        assertFalse(isVisible)
    }

    @Test
    fun isSyncBannerVisible_inLandscapeWhenDismissed_returnsFalse() {
        val isVisible = DashboardActivity.isSyncBannerVisible(isLandscape = true, isDismissed = true)
        assertFalse(isVisible)
    }
}
