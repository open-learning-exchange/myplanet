package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormatSymbols
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.databinding.FragmentActivitiesBinding
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class ActivitiesFragment : BaseBindingFragment<FragmentActivitiesBinding>(FragmentActivitiesBinding::inflate) {
    private val months = DateFormatSymbols().months
    private val viewModel: ActivitiesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val daynightTextColor = ResourcesCompat.getColor(resources, R.color.daynight_textColor, null)

        collectLatestWhenStarted(viewModel.monthlyLoginCounts) { monthlyCounts ->
            renderChart(monthlyCounts, daynightTextColor)
        }
    }

    private fun renderChart(monthlyCounts: Map<Int, Int>, textColor: Int) {
        if (monthlyCounts.isEmpty()) {
            binding.chart.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }

        binding.chart.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        val entries = monthlyCounts.entries
            .map { (month, count) -> BarEntry(month.toFloat(), count.toFloat()) }

        val label = getString(R.string.chart_label)
        val dataSet = BarDataSet(entries, label)
        val barData = BarData(dataSet)

        binding.chart.apply {
            description.isEnabled = false
            data = barData
            setFitBars(true)
            setExtraOffsets(8f, 8f, 8f, 8f)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                this.textColor = textColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return getMonth(value.toInt())
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                this.textColor = textColor
            }
            axisRight.isEnabled = false
            legend.apply {
                this.textColor = textColor
                isWordWrapEnabled = true
            }
            this.data.setValueTextColor(textColor)
            this.data.setValueTextSize(10f)
            invalidate()
        }
    }

    internal fun getMonth(month: Int): String {
        return months[month]
    }
}
