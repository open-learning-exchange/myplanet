package org.ole.planet.myplanet.ui.viewer

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Rational
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.databinding.ActivityResourceViewerBinding
import org.ole.planet.myplanet.ui.ratings.RatingsFragment
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils

@AndroidEntryPoint
class ResourceViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResourceViewerBinding
    @Inject
    lateinit var userRepository: UserRepository
    private val viewModel: ResourceViewerViewModel by viewModels()
    private var backNavigationHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResourceViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        if (savedInstanceState == null) {
            val resourceId = intent.getStringExtra("resourceId")
            val filePath = intent.getStringExtra("TOUCHED_FILE")
            val title = intent.getStringExtra("RESOURCE_TITLE")
            val isOnline = intent.getBooleanExtra("isOnline", false)
            val isFullPath = intent.getBooleanExtra("isFullPath", false)
            val auth = intent.getStringExtra("Auth") ?: ""

            if (!isOnline && filePath != null) {
                try {
                    val file = if (isFullPath) File(filePath) else File(getExternalFilesDir(null), "ole/$filePath")
                    val canonicalPath = file.canonicalPath

                    val isAllowed = if (isFullPath) {
                        val allowedRoots = listOfNotNull(
                            getExternalFilesDir(null)?.canonicalPath,
                            externalCacheDir?.canonicalPath,
                            Environment.getExternalStorageDirectory()?.canonicalPath
                        )
                        allowedRoots.any { canonicalPath.startsWith(it + File.separator) || canonicalPath == it }
                    } else {
                        val baseDir = File(getExternalFilesDir(null), "ole").canonicalPath
                        canonicalPath.startsWith(baseDir + File.separator) || canonicalPath == baseDir
                    }

                    if (!isAllowed) {
                        Log.w("ResourceViewer", "Rejected path: $canonicalPath")
                        finish()
                        return
                    }
                } catch (e: Exception) {
                    Log.e("ResourceViewer", "Error resolving path", e)
                    finish()
                    return
                }
            }

            val typeString = intent.getStringExtra("resourceType") ?: ResourceViewerFragment.ResourceType.UNKNOWN.name
            val type = try {
                ResourceViewerFragment.ResourceType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                Log.w("ResourceViewer", "Invalid resource type: $typeString")
                ResourceViewerFragment.ResourceType.UNKNOWN
            }

            val fragment = ResourceViewerFragment.newInstance(resourceId, filePath, title, type, isOnline, auth, isFullPath)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackNavigation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleBackNavigation() {
        if (backNavigationHandled) return
        val fragment = currentViewerFragment()
        val resourceId = intent.getStringExtra("resourceId")
        val title = intent.getStringExtra("RESOURCE_TITLE")

        if (fragment?.isResourceFinished() == true && !resourceId.isNullOrBlank()) {
            lifecycleScope.launch {
                val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }
                if (userId == null) {
                    finish()
                    return@launch
                }

                if (backNavigationHandled) return@launch
                backNavigationHandled = true

                val showDialog = viewModel.shouldShowResourceRatingDialog(userId, resourceId)
                if (showDialog && !supportFragmentManager.isStateSaved) {
                    val dialog = RatingsFragment.newInstance("resource", resourceId, title)
                    dialog.setOnDismissListener { finish() }
                    viewModel.setRatingPrompted(userId, resourceId)
                    dialog.show(supportFragmentManager, RatingsFragment.TAG)
                } else {
                    finish()
                }
            }
        } else {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        tryEnterPictureInPicture()
    }

    fun tryEnterPictureInPicture(): Boolean {
        val fragment = currentViewerFragment()
        if (fragment?.isPlayingVideo() != true) return false
        val aspectRatio = fragment.getVideoAspectRatio() ?: Rational(16, 9)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()
        return try {
            enterPictureInPictureMode(params)
        } catch (e: IllegalStateException) {
            Log.w("ResourceViewer", "Unable to enter picture-in-picture mode", e)
            false
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            supportActionBar?.hide()
        } else {
            supportActionBar?.show()
        }
    }

    private fun currentViewerFragment(): ResourceViewerFragment? =
        supportFragmentManager.findFragmentById(R.id.fragment_container) as? ResourceViewerFragment
}
