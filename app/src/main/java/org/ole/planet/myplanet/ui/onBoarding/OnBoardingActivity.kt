package org.ole.planet.myplanet.ui.onBoarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityOnBoardingBinding
import org.ole.planet.myplanet.ui.SplashActivity

class OnBoardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnBoardingBinding
    private lateinit var mAdapter: OnBoardingAdapter
    private val onBoardItems = ArrayList<OnBoardItem>()
    private var dotsCount = 0
    private lateinit var dots: Array<ImageView?>

    companion object {
        const val PREFS_NAME = "OLE_PLANET"
        const val BOOLEAN_KEY = "isOnBoardingComplete"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnBoardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (sharedPreferences.getBoolean(BOOLEAN_KEY, false) == true) {
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish()
            return
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
        val imageId = intArrayOf(R.drawable.ole_logo, R.drawable.ole_logo, R.drawable.ole_logo, R.drawable.ole_logo);

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
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putBoolean(BOOLEAN_KEY, true)
        editor.apply()
        val main = Intent(this, SplashActivity::class.java)
        startActivity(main)
        finish()
    }
}
