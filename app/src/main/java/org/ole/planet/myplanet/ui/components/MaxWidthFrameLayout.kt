package org.ole.planet.myplanet.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class MaxWidthFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val maxWidthPx: Int = (MAX_WIDTH_DP * resources.displayMetrics.density).toInt()

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
