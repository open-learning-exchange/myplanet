package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.FinanceReport
import org.ole.planet.myplanet.ui.enterprises.EnterprisesReportsAdapter.Companion.PAYLOAD_KEY_NON_TEAM_MEMBER_CHANGED

@RunWith(AndroidJUnit4::class)
class EnterprisesReportsAdapterTest {

    private lateinit var adapter: EnterprisesReportsAdapter
    private lateinit var context: Context
    private lateinit var timeProvider: org.ole.planet.myplanet.utils.TestTimeProvider

    private fun createReport(
        id: String,
        description: String? = null,
        beginningBalance: Double = 0.0,
        sales: Double = 0.0,
        otherIncome: Double = 0.0,
        wages: Double = 0.0,
        otherExpenses: Double = 0.0,
        imageName: String? = null
    ) = FinanceReport(
        _id = id,
        _rev = "rev1",
        status = "active",
        description = description,
        beginningBalance = beginningBalance,
        sales = sales,
        otherIncome = otherIncome,
        wages = wages,
        otherExpenses = otherExpenses,
        startDate = 1000L,
        endDate = 2000L,
        createdDate = 500L,
        updatedDate = 600L,
        updated = false,
        imageName = imageName
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(AppCompatR.style.Theme_AppCompat)
        timeProvider = org.ole.planet.myplanet.utils.TestTimeProvider(currentTime = 1000L)
        adapter = EnterprisesReportsAdapter(context, "Test Team", {}, {}, timeProvider)
    }

    @Test
    fun testSetNonTeamMember_emitsPayload() {
        val team1 = createReport("team1")
        val team2 = createReport("team2")
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
        val team1 = createReport("team1")
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
        val team1 = createReport("team1")
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
    fun testReportTotals_calculatesIncomeExpensesProfitLossAndEndingBalance() {
        val report = createReport(
            id = "r1",
            sales = 500.0,
            otherIncome = 100.0,
            wages = 200.0,
            otherExpenses = 50.0,
            beginningBalance = 1000.0
        )

        val totals = reportTotals(report)

        assertEquals(600.0, totals.totalIncome, 0.0)
        assertEquals(250.0, totals.totalExpenses, 0.0)
        assertEquals(350.0, totals.profitLoss, 0.0)
        assertEquals(1350.0, totals.endingBalance, 0.0)
    }

    @Test
    fun testReportTotals_withNegativeBeginningBalance_calculatesEndingBalanceCorrectly() {
        val report = createReport(
            id = "r1",
            sales = 200.0,
            otherIncome = 50.0,
            wages = 100.0,
            otherExpenses = 50.0,
            beginningBalance = -500.0
        )

        val totals = reportTotals(report)

        assertEquals(250.0, totals.totalIncome, 0.0)
        assertEquals(150.0, totals.totalExpenses, 0.0)
        assertEquals(100.0, totals.profitLoss, 0.0)
        assertEquals(-400.0, totals.endingBalance, 0.0)
    }

    @Test
    fun testOnBindViewHolder_bindsReportTotalsToViews() {
        val report = createReport(
            id = "report1",
            sales = 300.0,
            otherIncome = 50.0,
            wages = 100.0,
            otherExpenses = 20.0,
            beginningBalance = -100.0
        )

        adapter.submitList(listOf(report)) {
            val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals("350", viewHolder.binding.totalIncomeValue.text.toString())
            assertEquals("120", viewHolder.binding.totalExpensesValue.text.toString())
            assertEquals("230", viewHolder.binding.profitLossValue.text.toString())
            assertEquals("130", viewHolder.binding.endingBalanceValue.text.toString())
        }
    }

    @Test
    fun testBindReportImage_missingFile_visibilityGone() {
        val report = createReport(
            id = "report1",
            imageName = "missing.jpg"
        )

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

            val report = createReport(
                id = "report1",
                imageName = "report.jpg"
            )

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

            val report = createReport(
                id = "report1",
                imageName = "report.jpg"
            )

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

                // Advance time past the 5000ms TTL deterministically without Thread.sleep
                timeProvider.advanceBy(5001L)

                // Re-bind after TTL expires re-stats disk -> VISIBLE
                adapter.onBindViewHolder(viewHolder, 0)
                assertEquals(View.VISIBLE, viewHolder.binding.reportImage.visibility)
            }
        } finally {
            io.mockk.unmockkObject(org.ole.planet.myplanet.utils.FileUtils)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testOnBindViewHolder_hoistedReportTitleResolvedOnceAcrossMultipleBinds() {
        val spyContext = spyk(context)
        val spyAdapter = EnterprisesReportsAdapter(spyContext, "Test Team", {}, {}, timeProvider)

        val report1 = createReport("report1")
        val report2 = createReport("report2")
        val list = listOf(report1, report2)

        spyAdapter.submitList(list) {
            val binding1 = ReportListItemBinding.inflate(LayoutInflater.from(spyContext))
            val viewHolder1 = EnterprisesReportsAdapter.ReportsViewHolder(binding1)
            val binding2 = ReportListItemBinding.inflate(LayoutInflater.from(spyContext))
            val viewHolder2 = EnterprisesReportsAdapter.ReportsViewHolder(binding2)

            spyAdapter.onBindViewHolder(viewHolder1, 0)
            spyAdapter.onBindViewHolder(viewHolder2, 1)

            verify(exactly = 1) { spyContext.getString(R.string.team_financial_report, "Test Team") }
        }
    }
}
