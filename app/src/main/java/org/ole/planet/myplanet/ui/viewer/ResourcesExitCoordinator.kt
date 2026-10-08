package org.ole.planet.myplanet.ui.viewer

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.ui.ratings.RatingsFragment

class ResourcesExitCoordinator(
    private val activity: AppCompatActivity,
    private val viewModel: ResourceViewerViewModel,
) {
    private var handled = false

    fun handleBackNavigation(resourceId: String?, title: String?, isResourceFinished: Boolean = true) {
        if (handled) return

        if (!isResourceFinished || resourceId.isNullOrBlank()) {
            activity.finish()
            return
        }

        activity.lifecycleScope.launch {
            if (handled) return@launch
            handled = true

            val showDialog = viewModel.shouldShowResourceRatingDialog(resourceId)
            if (showDialog && !activity.supportFragmentManager.isStateSaved) {
                val dialog = RatingsFragment.newInstance("resource", resourceId, title)
                dialog.setOnDismissListener { activity.finish() }
                viewModel.setRatingPrompted(resourceId)
                dialog.show(activity.supportFragmentManager, RatingsFragment.TAG)
            } else {
                activity.finish()
            }
        }
    }
}
