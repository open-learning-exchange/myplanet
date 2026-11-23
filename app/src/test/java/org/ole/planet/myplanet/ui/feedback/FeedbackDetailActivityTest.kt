package org.ole.planet.myplanet.ui.feedback

import android.content.Intent
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.model.Feedback
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

    @Inject
    lateinit var feedbackDao: FeedbackDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `rvFeedbackReply retains adapter instance across updates and updates itemCount on submitList`() {
        val initialFeedback = Feedback().apply {
            id = "feedback_123"
            openTime = 1000000000000L
            messages = """[
                {"message":"Initial message","user":"owner1","time":"1000000000000"},
                {"message":"First reply","user":"user1","time":"1000000000000"}
            ]"""
        }
        val updatedFeedback = Feedback().apply {
            id = "feedback_123"
            openTime = 1000000000000L
            messages = """[
                {"message":"Initial message","user":"owner1","time":"1000000000000"},
                {"message":"First reply","user":"user1","time":"1000000000000"},
                {"message":"Second reply","user":"user2","time":"1000000005000"}
            ]"""
        }

        runBlocking {
            feedbackDao.upsert(initialFeedback)
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), FeedbackDetailActivity::class.java).apply {
            putExtra("id", "feedback_123")
        }
        val controller = Robolectric.buildActivity(FeedbackDetailActivity::class.java, intent).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.rv_feedback_reply)
        while (recyclerView.adapter?.itemCount != 1) {
            ShadowLooper.idleMainLooper()
        }

        val initialAdapter = recyclerView.adapter
        assertNotNull(initialAdapter)
        assertEquals(1, initialAdapter!!.itemCount)

        runBlocking {
            feedbackDao.upsert(updatedFeedback)
        }

        val viewModel = activity.viewModels<FeedbackDetailViewModel>().value
        viewModel.loadFeedback("feedback_123")
        while (recyclerView.adapter?.itemCount != 2) {
            ShadowLooper.idleMainLooper()
        }

        assertSame(initialAdapter, recyclerView.adapter)
        assertEquals(2, recyclerView.adapter!!.itemCount)

        controller.destroy()
    }
}
