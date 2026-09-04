package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.ui.enterprises.EnterprisesReportsAdapter.Companion.PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED

@RunWith(AndroidJUnit4::class)
class EnterprisesReportsAdapterTest {

    private lateinit var adapter: EnterprisesReportsAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(AppCompatR.style.Theme_AppCompat)
        adapter = EnterprisesReportsAdapter(context, "Test Team", {}, {})
    }

    @Test
    fun testSetNonTeamMember_emitsPayload() {
        val team1 = MyTeam().apply { _id = "team1" }
        val team2 = MyTeam().apply { _id = "team2" }
        val list = listOf(team1, team2)

        var payloadEmitted: Any? = null
        val observer = object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                payloadEmitted = payload
            }
        }
        adapter.registerAdapterDataObserver(observer)

        adapter.submitList(list) {
            adapter.setNonTeamMember(true)
            assertEquals(PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED, payloadEmitted)
        }
    }

    @Test
    fun testOnBindViewHolder_withPayload_updatesButtonVisibility() {
        val team1 = MyTeam().apply { _id = "team1" }
        val list = listOf(team1)

        adapter.submitList(list) {
            val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

            // Initial visibility state based on nonTeamMember (false by default)
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.VISIBLE, viewHolder.binding.edit.visibility)
            assertEquals(View.VISIBLE, viewHolder.binding.delete.visibility)

            adapter.setNonTeamMember(true)

            // Explicitly test the partial payload logic independently
            val payloads = mutableListOf<Any>(PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED)
            adapter.onBindViewHolder(viewHolder, 0, payloads)

            assertEquals(View.GONE, viewHolder.binding.edit.visibility)
            assertEquals(View.GONE, viewHolder.binding.delete.visibility)
        }
    }

    @Test
    fun testOnBindViewHolder_withUnknownPayload_fallsBackToFullBind() {
        val team1 = MyTeam().apply { _id = "team1" }
        val list = listOf(team1)

        adapter.submitList(list) {
            val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

            val payloads = mutableListOf<Any>("UNKNOWN_PAYLOAD")

            viewHolder.binding.tvReportTitle.text = ""

            // Should fallback to full bind and set the title
            adapter.onBindViewHolder(viewHolder, 0, payloads)

            assertEquals(context.getString(R.string.team_financial_report, "Test Team"), viewHolder.binding.tvReportTitle.text.toString())
        }
    }

    @Test
    fun testBindReportImage_missingFile_visibilityGone() {
        val report = MyTeam().apply {
            _id = "report1"
            imageName = "missing.jpg"
        }

        adapter.submitList(listOf(report)) {
            val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals(View.GONE, viewHolder.binding.reportImage.visibility)
        }
    }

    @Test
    fun testBindReportImage_existingFile_cachesAndInvalidatesOnListChange() {
        val tempDir = java.io.File(context.cacheDir, "test_ole_reports_${System.currentTimeMillis()}").apply { mkdirs() }
        io.mockk.mockkObject(org.ole.planet.myplanet.utils.FileUtils)
        io.mockk.every { org.ole.planet.myplanet.utils.FileUtils.getOlePath(any()) } returns "${tempDir.absolutePath}/"

        try {
            val teamAttachmentsDir = java.io.File(tempDir, "team_attachments/report1").apply { mkdirs() }
            val imageFile = java.io.File(teamAttachmentsDir, "report.jpg")
            imageFile.createNewFile()

            val report = MyTeam().apply {
                _id = "report1"
                imageName = "report.jpg"
            }

            adapter.submitList(listOf(report)) {
                val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
                val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

                // First bind detects existing file and makes image visible
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.VISIBLE, viewHolder.binding.reportImage.visibility)

                // File is deleted on disk
                imageFile.delete()

                // Second bind within TTL uses cached exists value (true) so image remains visible
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.VISIBLE, viewHolder.binding.reportImage.visibility)

                // Re-submitting list invalidates cache via onCurrentListChanged
                adapter.submitList(listOf(report)) {
                    adapter.onBindViewHolder(viewHolder, 0)
                    // Cache was cleared, so disk re-check detects file is deleted and sets visibility to GONE
                    assertEquals(View.GONE, viewHolder.binding.reportImage.visibility)
                }
            }
        } finally {
            io.mockk.unmockkObject(org.ole.planet.myplanet.utils.FileUtils)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBindReportImage_cacheExpiresAfterTtl() {
        val tempDir = java.io.File(context.cacheDir, "test_ole_reports_ttl_${System.currentTimeMillis()}").apply { mkdirs() }
        io.mockk.mockkObject(org.ole.planet.myplanet.utils.FileUtils)
        io.mockk.every { org.ole.planet.myplanet.utils.FileUtils.getOlePath(any()) } returns "${tempDir.absolutePath}/"

        try {
            val teamAttachmentsDir = java.io.File(tempDir, "team_attachments/report1").apply { mkdirs() }
            val imageFile = java.io.File(teamAttachmentsDir, "report.jpg")

            val report = MyTeam().apply {
                _id = "report1"
                imageName = "report.jpg"
            }

            adapter.submitList(listOf(report)) {
                val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
                val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

                // File doesn't exist initially -> GONE (and cached false)
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.GONE, viewHolder.binding.reportImage.visibility)

                // File appears on disk (e.g. downloaded)
                imageFile.createNewFile()

                // Immediate re-bind within TTL uses cached false -> still GONE
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.GONE, viewHolder.binding.reportImage.visibility)

                // Sleep past the 5000ms TTL
                Thread.sleep(5100L)

                // Re-bind after TTL expires re-stats disk -> VISIBLE
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.VISIBLE, viewHolder.binding.reportImage.visibility)
            }
        } finally {
            io.mockk.unmockkObject(org.ole.planet.myplanet.utils.FileUtils)
            tempDir.deleteRecursively()
        }
    }
}
