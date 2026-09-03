package org.ole.planet.myplanet.ui.viewer

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.ui.ratings.RatingsFragment

/**
 * Shared back-navigation policy for the resource viewers: when a learner leaves a finished
 * resource, prompt them to rate it once, then finish the hosting activity. Both
 * [ResourceViewerActivity] and [WebViewActivity] delegate here so the prompt rules live in one
 * place.
 */
class ResourcesExitCoordinator(
    private val activity: AppCompatActivity,
    private val userRepository: UserRepository,
    private val viewModel: ResourceViewerViewModel,
) {
    private var handled = false

    /**
     * Call from every exit path (back press, up button, close control). Shows the rating dialog
     * and finishes on dismiss when [isResourceFinished] and a rating is still wanted, otherwise
     * finishes immediately.
     */
    fun handleBackNavigation(resourceId: String?, title: String?, isResourceFinished: Boolean = true) {
        if (handled) return

        if (!isResourceFinished || resourceId.isNullOrBlank()) {
            activity.finish()
            return
        }

        activity.lifecycleScope.launch {
            val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }
            if (userId == null) {
                activity.finish()
                return@launch
            }

            if (handled) return@launch
            handled = true

            val showDialog = viewModel.shouldShowResourceRatingDialog(userId, resourceId)
            if (showDialog && !activity.supportFragmentManager.isStateSaved) {
                val dialog = RatingsFragment.newInstance("resource", resourceId, title)
                dialog.setOnDismissListener { activity.finish() }
                viewModel.setRatingPrompted(userId, resourceId)
                dialog.show(activity.supportFragmentManager, RatingsFragment.TAG)
            } else {
                activity.finish()
            }
        }
    }
}
