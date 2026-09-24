package org.ole.planet.myplanet.ui.onboarding

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.OnboardingItemBinding
import org.ole.planet.myplanet.model.OnboardingItem

class OnboardingAdapter(private val mContext: Context, private val onBoardItems: ArrayList<OnboardingItem>) : PagerAdapter() {

    override fun getCount(): Int {
        return onBoardItems.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = OnboardingItemBinding.inflate(LayoutInflater.from(mContext), container, false)

        val item = onBoardItems[position]
        binding.ivOnboard.setImageResource(item.imageID)
        binding.tvHeader.text = item.title
        binding.tvHeader.setTextColor(mContext.getColor(R.color.daynight_textColor))
        binding.tvDesc.text = item.description
        binding.tvDesc.setTextColor(mContext.getColor(R.color.daynight_textColor))
        container.addView(binding.root)

        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}
