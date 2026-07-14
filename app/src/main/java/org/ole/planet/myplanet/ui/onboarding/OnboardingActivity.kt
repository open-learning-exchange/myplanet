package org.ole.planet.myplanet.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityOnboardingBinding
import org.ole.planet.myplanet.model.OnboardingItem
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.surveys.PublicSurveyActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.MapTileUtils.copyAssets
import org.ole.planet.myplanet.utils.SecurePrefs

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var mAdapter: OnboardingAdapter
    private val onBoardItems = ArrayList<OnboardingItem>()
    private var dotsCount = 0
    private lateinit var dots: Array<ImageView?>
    @Inject
    lateinit var prefData: SharedPrefManager
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTaskRoot) {
            val chooserUri = webLinkForAppChooser(intent)
            if (chooserUri != null) {
                showAppChooser(chooserUri, onOpenHere = { routeDeepLinkIntoRunningApp() }, onCancel = { finish() })
                return
            }
            if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                routeDeepLinkIntoRunningApp()
                return
            }
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        copyAssets(this)

        val chooserUri = webLinkForAppChooser(intent)
        if (chooserUri != null) {
            showAppChooser(chooserUri, onOpenHere = { proceedWithLaunch() }, onCancel = { finish() })
            return
        }
        proceedWithLaunch()
    }

    private fun proceedWithLaunch() {
        if (handleDeepLinkIntent(intent)) return

        if (prefData.isLoggedIn() && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)) {
            startActivity(buildDashboardIntent())
            finish()
            return
        }

        if (prefData.getFirstLaunch()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            val (savedUser, savedPass) = withContext(dispatcherProvider.io) {
                Pair(
                    SecurePrefs.getUserName(this@OnboardingActivity, prefData.rawPreferences),
                    SecurePrefs.getPassword(this@OnboardingActivity, prefData.rawPreferences)
                )
            }
            if (!savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty() && !prefData.isLoggedIn()) {
                prefData.setLoggedIn(true)
            }
            if (prefData.isLoggedIn() && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)) {
                startActivity(buildDashboardIntent())
                finish()
            }
        }

        loadData()
        mAdapter = OnboardingAdapter(this, onBoardItems)
        binding.pagerIntroduction.adapter = mAdapter
        binding.pagerIntroduction.currentItem = 0
        binding.pagerIntroduction.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                for (i in dots.indices) {
                    dots[i]?.setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.non_selected_item_dot))
                }
                dots[position]?.setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.selected_item_dot))

                if (position == mAdapter.count - 1) {
                    binding.skip.visibility = View.GONE
                    binding.next.setText(R.string.get_started)
                } else {
                    binding.skip.visibility = View.VISIBLE
                    binding.next.setText(R.string.next)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.skip.setOnClickListener{
            finishTutorial()
        }

        binding.next.setOnClickListener {
            val currentPosition = binding.pagerIntroduction.currentItem
            if (currentPosition < mAdapter.count - 1) {
                binding.pagerIntroduction.setCurrentItem(currentPosition + 1, true)
            } else {
                finishTutorial()
            }
        }

        setUiPageViewController()
    }

    private fun loadData() {
        val descriptionResourceLists = listOf(
            listOf(R.string.ob_desc1),
            listOf(R.string.ob_desc2_1, R.string.ob_desc2_2),
            listOf(R.string.ob_desc3_1, R.string.ob_desc3_2),
            listOf(R.string.ob_desc4_1, R.string.ob_desc4_2, R.string.ob_desc4_3, R.string.ob_desc4_4, R.string.ob_desc4_5)
        )
        val headers = listOf(
            R.string.welcome_to_myPlanet,
            R.string.learn_offline,
            R.string.open_learning,
            R.string.unleash_learning_power
        )
        val imageIds = listOf(R.drawable.ole_logo, R.drawable.o_a, R.drawable.b_b, R.drawable.c_c)

        val items = imageIds.zip(headers).mapIndexed { index, (imageRes, headerRes) ->
            val descResourceArray = descriptionResourceLists.getOrNull(index).orEmpty()
            val description = descResourceArray.joinToString(separator = "\n") { getString(it) }
                .let { if (it.isEmpty()) it else "$it\n" }

            OnboardingItem().apply {
                imageID = imageRes
                title = getString(headerRes)
                this.description = description
            }
        }

        onBoardItems.clear()
        onBoardItems.addAll(items)
    }

    private fun setUiPageViewController() {
        dotsCount = mAdapter.count
        if (dotsCount <= 0) return
        dots = arrayOfNulls(dotsCount)

        for (i in dots.indices) {
            dots[i] = ImageView(this)
            dots[i]?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.non_selected_item_dot))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.setMargins(6, 0, 6, 0)
            binding.viewPagerCountDots.addView(dots[i], params)
        }
        dots[0]?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.selected_item_dot))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val chooserUri = webLinkForAppChooser(intent)
        if (chooserUri != null) {
            showAppChooser(chooserUri, onOpenHere = { handleDeepLinkIntent(intent) }, onCancel = {})
            return
        }
        handleDeepLinkIntent(intent)
    }

    private fun routeDeepLinkIntoRunningApp() {
        if (handleDeepLinkIntent(intent)) return
        val hasPendingSection = prefData.getRawString(DEEP_LINK_SECTION_KEY).isNotEmpty()
        if (hasPendingSection && prefData.isLoggedIn() && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)) {
            startActivity(buildDashboardIntent())
        }
        finish()
    }

    private fun webLinkForAppChooser(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_VIEW) return null
        if (intent.getBooleanExtra(EXTRA_SKIP_APP_CHOOSER, false)) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        return if (isLiteInstalled()) uri else null
    }

    private fun isLiteInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(LITE_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun showAppChooser(uri: Uri, onOpenHere: () -> Unit, onCancel: () -> Unit) {
        when (prefData.getRawString(DEEP_LINK_PREFERRED_APP_KEY)) {
            packageName -> {
                onOpenHere()
                return
            }
            LITE_PACKAGE_NAME -> {
                forwardToLite(uri, onOpenHere)
                return
            }
        }
        val ownLabel = applicationInfo.loadLabel(packageManager).toString()
        val liteLabel = try {
            packageManager.getApplicationInfo(LITE_PACKAGE_NAME, 0).loadLabel(packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            onOpenHere()
            return
        }
        var selected = -1
        val applyChoice = { remember: Boolean ->
            if (remember) {
                val target = if (selected == 0) packageName else LITE_PACKAGE_NAME
                prefData.setRawString(DEEP_LINK_PREFERRED_APP_KEY, target)
            }
            if (selected == 0) onOpenHere() else forwardToLite(uri, onOpenHere)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.open_link_with)
            .setSingleChoiceItems(arrayOf(ownLabel, liteLabel), -1) { d, which ->
                selected = which
                (d as? AlertDialog)?.let {
                    it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    it.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                }
            }
            .setPositiveButton(R.string.always) { _, _ -> applyChoice(true) }
            .setNegativeButton(R.string.just_once) { _, _ -> applyChoice(false) }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
        dialog.show()
    }

    private fun forwardToLite(uri: Uri, onForwardFailed: () -> Unit) {
        val forward = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(LITE_PACKAGE_NAME)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .putExtra(EXTRA_SKIP_APP_CHOOSER, true)
        try {
            startActivity(forward)
            finish()
        } catch (e: ActivityNotFoundException) {
            onForwardFailed()
        }
    }

    /**
     * Handles a deep link. Returns true when the link was fully consumed by launching
     * another activity (this activity is finishing) — callers must stop routing then.
     */
    private fun handleDeepLinkIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val uri: Uri = intent.data ?: return false
        if (maybeLaunchPublicSurvey(uri)) return true
        val (section, contentId) = when (uri.scheme) {
            "myplanet" -> {
                val sec = uri.host ?: return false
                Pair(sec, uri.pathSegments.firstOrNull())
            }
            "http", "https" -> {
                val segments = uri.pathSegments
                if (segments.firstOrNull().equals("survey", ignoreCase = true)) {
                    Pair("surveys", segments.getOrNull(2))
                } else {
                    val appIndex = segments.indexOf("app")
                    val sec = segments.getOrNull(appIndex + 1) ?: return false
                    val id = segments.getOrNull(appIndex + 2)
                    Pair(sec, id)
                }
            }
            else -> return false
        }
        prefData.setRawString(DEEP_LINK_SECTION_KEY, section)
        if (contentId != null) prefData.setRawString(DEEP_LINK_ID_KEY, contentId)
        else prefData.removeKey(DEEP_LINK_ID_KEY)
        return false
    }

    // publicAccess surveys are answerable without login via the server's public API
    private fun maybeLaunchPublicSurvey(uri: Uri): Boolean {
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val segments = uri.pathSegments
        if (!segments.firstOrNull().equals("survey", ignoreCase = true)) return false
        if (prefData.isLoggedIn()) return false
        val teamId = segments.getOrNull(1) ?: return false
        val surveyId = segments.getOrNull(2) ?: return false
        val serverBase = "${uri.scheme}://${uri.encodedAuthority}"
        startActivity(PublicSurveyActivity.newIntent(this, serverBase, teamId, surveyId))
        finish()
        return true
    }

    private fun buildDashboardIntent(): Intent {
        val dashIntent = Intent(applicationContext, DashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("from_login", true)
        val section = prefData.getRawString(DEEP_LINK_SECTION_KEY)
        if (section.isNotEmpty()) {
            dashIntent.putExtra("fragmentToOpen", section)
            prefData.removeKey(DEEP_LINK_SECTION_KEY)
            val contentId = prefData.getRawString(DEEP_LINK_ID_KEY)
            if (contentId.isNotEmpty()) {
                dashIntent.putExtra("contentId", contentId)
                prefData.removeKey(DEEP_LINK_ID_KEY)
            }
        }
        return dashIntent
    }

    private fun finishTutorial() {
        prefData.setFirstLaunch(true)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        const val DEEP_LINK_SECTION_KEY = "pending_deep_link_section"
        const val DEEP_LINK_ID_KEY = "pending_deep_link_id"
        const val EXTRA_SKIP_APP_CHOOSER = "org.ole.planet.myplanet.extra.SKIP_APP_CHOOSER"
        private const val LITE_PACKAGE_NAME = "org.ole.planet.myplanet.lite"
        private const val DEEP_LINK_PREFERRED_APP_KEY = "deep_link_preferred_app"
    }
}
