package org.ole.planet.myplanet.ui.onBoarding

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import org.ole.planet.myplanet.R

class OnBoardingAdapter(private val mContext: Context, private val onBoardItems: ArrayList<OnBoardItem>) : PagerAdapter() {

    override fun getCount(): Int {
        return onBoardItems.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemView = LayoutInflater.from(mContext).inflate(R.layout.onboard_item, container, false)

        val item = onBoardItems[position]
        val imageView = itemView.findViewById<ImageView>(R.id.iv_onboard)
        imageView.setImageResource(item.imageID)
        val tvTitle = itemView.findViewById<TextView>(R.id.tv_header)
        tvTitle.text = item.title
        tvTitle.setTextColor(mContext.getColor(R.color.daynight_textColor))
        val tvContent = itemView.findViewById<TextView>(R.id.tv_desc)
        tvContent.text = item.description
        tvContent.setTextColor(mContext.getColor(R.color.daynight_textColor))
        container.addView(itemView)

        return itemView
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}