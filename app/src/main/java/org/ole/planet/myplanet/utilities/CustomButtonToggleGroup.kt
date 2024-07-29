package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class CustomButtonToggleGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    private val radius: Float = 25f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        for (i in 0 until childCount) {
            val button = getChildAt(i) as? MaterialButton ?: continue
            if (button.visibility == GONE) {
                continue
            }
            val builder = button.shapeAppearanceModel.toBuilder()
            button.shapeAppearanceModel = builder
                .setTopLeftCornerSize(radius)
                .setBottomLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomRightCornerSize(radius)
                .build()
        }
    }
}
