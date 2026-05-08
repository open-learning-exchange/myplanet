package org.ole.planet.myplanet.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityOnboardingBinding
import org.ole.planet.myplanet.model.OnboardingItem
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.Constants
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        copyAssets(this)
        handleDeepLinkIntent(intent)

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
            val (savedUser, savedPass) = withContext(Dispatchers.IO) {
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
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        val (section, contentId) = when (uri.scheme) {
            // myplanet://courses  or  myplanet://courses/abc123
            "myplanet" -> {
                val sec = uri.host ?: return
                Pair(sec, uri.pathSegments.firstOrNull())
            }
            // https://planet.learning.ole.org/app/courses  or  /app/courses/abc123
            "http", "https" -> {
                val segments = uri.pathSegments
                val appIndex = segments.indexOf("app")
                val sec = segments.getOrNull(appIndex + 1) ?: return
                val id = segments.getOrNull(appIndex + 2)
                Pair(sec, id)
            }
            else -> return
        }
        prefData.setRawString(DEEP_LINK_SECTION_KEY, section)
        if (contentId != null) prefData.setRawString(DEEP_LINK_ID_KEY, contentId)
        else prefData.removeKey(DEEP_LINK_ID_KEY)
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
    }
}
