package org.ole.planet.myplanet.ui.feedback

import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FeedbackDetailActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `rvFeedbackReply sets adapter in setUpReplies during onCreate and retains adapter instance across updates`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), FeedbackDetailActivity::class.java).apply {
            putExtra("id", "feedback_123")
        }
        val controller = Robolectric.buildActivity(FeedbackDetailActivity::class.java, intent).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.rv_feedback_reply)
        val initialAdapter = recyclerView.adapter
        assertNotNull(initialAdapter)

        val field = FeedbackDetailActivity::class.java.getDeclaredField("viewModel\$delegate")
        field.isAccessible = true
        val viewModel = (field.get(activity) as Lazy<*>).value as FeedbackDetailViewModel

        viewModel.loadFeedback("feedback_123")
        ShadowLooper.idleMainLooper()

        assertSame(initialAdapter, recyclerView.adapter)

        controller.destroy()
    }
}
