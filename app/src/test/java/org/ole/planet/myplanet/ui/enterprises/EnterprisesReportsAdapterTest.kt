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
    fun testReportTotals_calculatesIncomeExpensesProfitLossAndEndingBalance() {
        val report = MyTeam().apply {
            sales = 500
            otherIncome = 100
            wages = 200
            otherExpenses = 50
            beginningBalance = 1000
        }

        val totals = reportTotals(report)

        assertEquals(600, totals.totalIncome)
        assertEquals(250, totals.totalExpenses)
        assertEquals(350, totals.profitLoss)
        assertEquals(1350, totals.endingBalance)
    }

    @Test
    fun testReportTotals_withNegativeBeginningBalance_calculatesEndingBalanceCorrectly() {
        val report = MyTeam().apply {
            sales = 200
            otherIncome = 50
            wages = 100
            otherExpenses = 50
            beginningBalance = -500
        }

        val totals = reportTotals(report)

        assertEquals(250, totals.totalIncome)
        assertEquals(150, totals.totalExpenses)
        assertEquals(100, totals.profitLoss)
        assertEquals(-400, totals.endingBalance)
    }

    @Test
    fun testOnBindViewHolder_bindsReportTotalsToViews() {
        val report = MyTeam().apply {
            _id = "report1"
            sales = 300
            otherIncome = 50
            wages = 100
            otherExpenses = 20
            beginningBalance = -100
        }

        adapter.submitList(listOf(report)) {
            val binding = ReportListItemBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesReportsAdapter.ReportsViewHolder(binding)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals(context.getString(R.string.number_placeholder, 350), viewHolder.binding.totalIncomeValue.text.toString())
            assertEquals(context.getString(R.string.number_placeholder, 120), viewHolder.binding.totalExpensesValue.text.toString())
            assertEquals(context.getString(R.string.number_placeholder, 230), viewHolder.binding.profitLossValue.text.toString())
            assertEquals(context.getString(R.string.number_placeholder, 130), viewHolder.binding.endingBalanceValue.text.toString())
        }
    }
}
