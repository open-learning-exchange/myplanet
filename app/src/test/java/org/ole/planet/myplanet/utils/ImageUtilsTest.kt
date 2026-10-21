package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.BaseRequestOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
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
@Config(manifest = Config.NONE, application = Application::class)
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

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadImage_WithValidUrlAndSize() {
        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadImage(userImage, imageView, 100)

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

    @Test
    fun testLoadPlaceholderImage_WithValidUrl() {
        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadPlaceholderImage(userImage, imageView)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadPlaceholderImage_WithValidUrlAndSize() {
        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadPlaceholderImage(userImage, imageView, 150)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadPlaceholderImage_WithNullUrl() {
        ImageUtils.loadPlaceholderImage(null, imageView)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadProfileImage_WithValidUrl() {
        val userImage = "http://example.com/image.jpg"
        ImageUtils.loadProfileImage(userImage, imageView, 200)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadProfileImage_WithNullUrl() {
        ImageUtils.loadProfileImage(null, imageView, 200)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadProfileImage_WithEmptyUrl() {
        ImageUtils.loadProfileImage("", imageView, 200)

        val drawable = imageView.drawable
        assertNotNull(drawable)
        val shadowDrawable = shadowOf(drawable)
        assertEquals(R.drawable.profile, shadowDrawable.createdFromResId)
    }

    @Test
    fun testLoadProfileImage_ClearsInFlightRequest_WhenNullUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadProfileImage(null, spiedImageView, 200)

            verifyOrder {
                requestManager.clear(spiedImageView)
                spiedImageView.setImageResource(R.drawable.profile)
            }
        } finally {
            unmockkStatic(Glide::class)
        }
    }

    @Test
    fun testLoadProfileImage_ClearsInFlightRequest_WhenEmptyUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadProfileImage("", spiedImageView, 200)

            verifyOrder {
                requestManager.clear(spiedImageView)
                spiedImageView.setImageResource(R.drawable.profile)
            }
        } finally {
            unmockkStatic(Glide::class)
        }
    }

    @Test
    fun testLoadProfileImage_DoesNotClearRequest_WhenNonEmptyUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        val requestBuilder = mockk<RequestBuilder<android.graphics.drawable.Drawable>>()
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            every { requestManager.load(any<String>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).diskCacheStrategy(any()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).override(any<Int>(), any<Int>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).circleCrop() } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).placeholder(any<Int>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).error(any<Int>()) } returns requestBuilder
            every { requestBuilder.into(any<ImageView>()) } returns mockk(relaxed = true)

            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadProfileImage("http://example.com/image.jpg", spiedImageView, 200)

            verify(exactly = 0) { requestManager.clear(spiedImageView) }
        } finally {
            unmockkStatic(Glide::class)
        }
    }

    @Test
    fun testLoadImage_ClearsInFlightRequest_WhenNullUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadImage(null, spiedImageView)

            verifyOrder {
                requestManager.clear(spiedImageView)
                spiedImageView.setImageResource(R.drawable.ole_logo)
            }
        } finally {
            unmockkStatic(Glide::class)
        }
    }

    @Test
    fun testLoadImage_ClearsInFlightRequest_WhenEmptyUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadImage("", spiedImageView)

            verifyOrder {
                requestManager.clear(spiedImageView)
                spiedImageView.setImageResource(R.drawable.ole_logo)
            }
        } finally {
            unmockkStatic(Glide::class)
        }
    }

    @Test
    fun testLoadImage_DoesNotClearRequest_WhenNonEmptyUrl() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        val requestBuilder = mockk<RequestBuilder<android.graphics.drawable.Drawable>>()
        mockkStatic(Glide::class)
        try {
            every { Glide.with(any<Context>()) } returns requestManager
            every { requestManager.load(any<String>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).diskCacheStrategy(any()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).override(any<Int>(), any<Int>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).circleCrop() } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).placeholder(any<Int>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).error(any<Int>()) } returns requestBuilder
            every { (requestBuilder as BaseRequestOptions<*>).apply(any()) } returns requestBuilder
            every { requestBuilder.into(any<ImageView>()) } returns mockk(relaxed = true)

            val spiedImageView = spyk(ImageView(context))
            every { spiedImageView.isOpaque } returns false

            ImageUtils.loadImage("http://example.com/image.jpg", spiedImageView)

            verify(exactly = 0) { requestManager.clear(spiedImageView) }
        } finally {
            unmockkStatic(Glide::class)
        }
    }
}
