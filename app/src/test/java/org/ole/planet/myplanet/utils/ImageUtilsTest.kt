package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [33])
class ImageUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var imageView: ImageView

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        imageView = ImageView(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadImage_WithValidUrl() {
        mockkStatic(Glide::class)
        val requestManager = mockk<RequestManager>(relaxed = true)
        val requestBuilder = mockk<RequestBuilder<android.graphics.drawable.Drawable>>(relaxed = true)

        every { Glide.with(any<Context>()) } returns requestManager
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        every { requestBuilder.circleCrop() } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder

        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadImage(userImage, imageView)

        verify { Glide.with(context) }
        verify { requestManager.load(userImage) }
        verify { requestBuilder.diskCacheStrategy(DiskCacheStrategy.ALL) }
        verify { requestBuilder.circleCrop() }
        verify { requestBuilder.placeholder(R.drawable.profile) }
        verify { requestBuilder.error(R.drawable.profile) }
        verify { requestBuilder.into(imageView) }
    }

    @Test
    fun testLoadImage_WithNullUrl() {
        val spiedImageView = spyk(imageView)

        ImageUtils.loadImage(null, spiedImageView)

        verify { spiedImageView.setImageResource(R.drawable.ole_logo) }
    }

    @Test
    fun testLoadImage_WithEmptyUrl() {
        val spiedImageView = spyk(imageView)

        ImageUtils.loadImage("", spiedImageView)

        verify { spiedImageView.setImageResource(R.drawable.ole_logo) }
    }
}
