package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.R as AppCompatR
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.ReportListItemBinding
import org.ole.planet.myplanet.model.MyTeam
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = android.app.Application::class)
class EnterprisesReportsFragmentTest {

    private fun BaseTeamFragment.callGetEffectiveTeamName(): String {
        val method = BaseTeamFragment::class.java.getDeclaredMethod("getEffectiveTeamName")
        method.isAccessible = true
        return method.invoke(this) as String
    }

    @Test
    fun `getEffectiveTeamName returns teamName from arguments when present`() {
        val fragment = spyk<EnterprisesReportsFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns "Alpha Enterprise"
        fragment.team = MyTeam(name = "Loaded Enterprise")

        val effectiveName = fragment.callGetEffectiveTeamName()
        assertEquals("Alpha Enterprise", effectiveName)
    }

    @Test
    fun `getEffectiveTeamName falls back to loaded team when argument is blank`() {
        val fragment = spyk<EnterprisesReportsFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns ""
        fragment.team = MyTeam(name = "Loaded Enterprise")

        val effectiveName = fragment.callGetEffectiveTeamName()
        assertEquals("Loaded Enterprise", effectiveName)
    }

    @Test
    fun `effective team name is passed to adapter for report title`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(AppCompatR.style.Theme_AppCompat)

        val fragment = spyk<EnterprisesReportsFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns "Alpha Enterprise"

        val effectiveName = fragment.callGetEffectiveTeamName()
        val adapter = EnterprisesReportsAdapter(context, effectiveName, {}, {})

        val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
        val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

        adapter.submitList(listOf(MyTeam().apply { _id = "r1"; description = "Summary" })) {
            adapter.onBindViewHolder(viewHolder, 0)
            val expectedTitle = context.getString(R.string.team_financial_report, "Alpha Enterprise")
            assertEquals(expectedTitle, binding.tvReportTitle.text.toString())
        }
    }

    @Test
    fun `effective team name is used to generate CSV filename and export content`() = runTest {
        val fragment = spyk<EnterprisesReportsFragment>()
        val mockBundle = mockk<Bundle>()
        val viewModel = mockk<EnterprisesViewModel>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns "Alpha Enterprise"
        fragment.teamId = "team123"

        val effectiveTeamName = fragment.callGetEffectiveTeamName()
        val formattedTitle = "Report_of_${effectiveTeamName.replace(" ", "_")}_Financial_Report_Summary_on_2025-01-01"

        coEvery { viewModel.exportReportsAsCsv("team123", effectiveTeamName) } returns
            "$effectiveTeamName Financial Report Summary\n\nStart Date, End Date..."

        val exportedCsv = viewModel.exportReportsAsCsv(fragment.teamId, effectiveTeamName)

        coVerify(exactly = 1) { viewModel.exportReportsAsCsv("team123", "Alpha Enterprise") }
        assertEquals("Report_of_Alpha_Enterprise_Financial_Report_Summary_on_2025-01-01", formattedTitle)
        assertTrue(exportedCsv.startsWith("Alpha Enterprise Financial Report Summary"))
    }
}
