package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class ImageUtilsTest {

    private lateinit var context: Context
    private lateinit var imageView: ImageView

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        imageView = ImageView(context)
    }

    @Test
    fun testLoadImage_WithValidUrl() {
        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadImage(userImage, imageView)

        // Glide should set the placeholder synchronously on the main thread
        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadImage_WithNullUrl() {
        ImageUtils.loadImage(null, imageView)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.ole_logo, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadImage_WithEmptyUrl() {
        ImageUtils.loadImage("", imageView)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.ole_logo, shadowDrawable.createdFromResId)
    }
}
