package org.ole.planet.myplanet.ui.onBoarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityOnBoardingBinding
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils.copyAssets
import org.ole.planet.myplanet.utilities.SharedPrefManager

class OnBoardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnBoardingBinding
    private lateinit var mAdapter: OnBoardingAdapter
    private val onBoardItems = ArrayList<OnBoardItem>()
    private var dotsCount = 0
    private lateinit var dots: Array<ImageView?>
    lateinit var prefData: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnBoardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefData = SharedPrefManager(this)

        copyAssets(this)
        val settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (settings.getBoolean(Constants.KEY_LOGIN, false) && !Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext)) {
            val dashboard = Intent(applicationContext, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(dashboard)
            finish()
            return
        }

        if (prefData.getFirstLaunch()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadData()
        mAdapter = OnBoardingAdapter(this, onBoardItems)
        binding.pagerIntroduction.adapter = mAdapter
        binding.pagerIntroduction.currentItem = 0
        binding.pagerIntroduction.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                for (i in 0 until dotsCount) {
                    dots[i]?.setImageDrawable(ContextCompat.getDrawable(this@OnBoardingActivity, R.drawable.non_selected_item_dot))
                }
                dots[position]?.setImageDrawable(ContextCompat.getDrawable(this@OnBoardingActivity, R.drawable.selected_item_dot))

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
        val descArrayPage1 = intArrayOf(R.string.ob_desc1)
        val descArrayPage2 = intArrayOf(R.string.ob_desc2_1, R.string.ob_desc2_2)
        val descArrayPage3 = intArrayOf(R.string.ob_desc3_1, R.string.ob_desc3_2)
        val descArrayPage4 = intArrayOf(R.string.ob_desc4_1, R.string.ob_desc4_2, R.string.ob_desc4_3, R.string.ob_desc4_4, R.string.ob_desc4_5)
        val header = intArrayOf(R.string.welcome_to_myPlanet, R.string.learn_offline, R.string.open_learning, R.string.unleash_learning_power)
        val imageId = intArrayOf(R.drawable.ole_logo, R.drawable.o_a, R.drawable.b_b, R.drawable.c_c)

        for (i in imageId.indices) {
            val descResourceArray = when (i) {
                0 -> descArrayPage1
                1 -> descArrayPage2
                2 -> descArrayPage3
                3 -> descArrayPage4
                else -> intArrayOf()
            }

            val stringBuilder = StringBuilder()

            for (descResId in descResourceArray) {
                stringBuilder.append(resources.getString(descResId)).append("\n")
            }

            val item = OnBoardItem().apply {
                imageID = imageId[i]
                title = resources.getString(header[i])
                description = stringBuilder.toString()
            }
            onBoardItems.add(item)
        }
    }

    private fun setUiPageViewController() {
        dotsCount = mAdapter.count
        dots = arrayOfNulls(dotsCount)

        for (i in 0 until dotsCount) {
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

    private fun finishTutorial() {
        prefData.setFirstLaunch(true)
        startActivity(Intent(this, LoginActivity::class.java))
    }
}
