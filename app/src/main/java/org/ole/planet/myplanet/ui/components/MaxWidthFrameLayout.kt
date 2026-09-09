package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import org.ole.planet.myplanet.R

class MaxWidthFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val maxWidthPx: Int = run {
        val customWidthPx = attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.MaxWidthFrameLayout)
            val width = typedArray.getDimensionPixelSize(R.styleable.MaxWidthFrameLayout_maxContentWidth, -1)
            typedArray.recycle()
            width
        } ?: -1
        if (customWidthPx > 0) customWidthPx else (MAX_WIDTH_DP * resources.displayMetrics.density).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = MeasureSpec.getSize(widthMeasureSpec)
        if (originalWidth > maxWidthPx) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            super.onMeasure(MeasureSpec.makeMeasureSpec(maxWidthPx, mode), heightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    companion object {
        private const val MAX_WIDTH_DP = 1040
    }
}
