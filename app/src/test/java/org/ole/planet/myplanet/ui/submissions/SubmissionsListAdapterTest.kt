package org.ole.planet.myplanet.ui.submissions

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.SubmissionItem
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SubmissionsListAdapterTest {

    private lateinit var activity: Activity
    private lateinit var onGeneratePdf: (String?) -> Unit
    private lateinit var adapter: SubmissionsListAdapter

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        onGeneratePdf = mockk(relaxed = true)
        adapter = SubmissionsListAdapter(activity, null, onGeneratePdf)
    }

    @Test
    fun `click download pdf on recycled holder receives new submission id`() {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        val itemA = SubmissionItem(id = "sub_A", status = "pending", uploaded = false, lastUpdateTime = 1000L)
        val itemB = SubmissionItem(id = "sub_B", status = "completed", uploaded = true, lastUpdateTime = 2000L)

        val latch1 = CountDownLatch(1)
        adapter.submitList(listOf(itemA)) { latch1.countDown() }
        shadowOf(Looper.getMainLooper()).idle()
        latch1.await(2, TimeUnit.SECONDS)
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val latch2 = CountDownLatch(2)
        adapter.submitList(null) { latch2.countDown() }
        adapter.submitList(listOf(itemB)) { latch2.countDown() }
        shadowOf(Looper.getMainLooper()).idle()
        latch2.await(2, TimeUnit.SECONDS)
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val holder = recyclerView.findViewHolderForAdapterPosition(0)
        assertNotNull(holder)

        val btnDownloadPdf = holder!!.itemView.findViewById<Button>(R.id.btn_download_pdf)
        btnDownloadPdf.performClick()

        verify(exactly = 1) { onGeneratePdf("sub_B") }
        verify(exactly = 0) { onGeneratePdf("sub_A") }
    }
}
