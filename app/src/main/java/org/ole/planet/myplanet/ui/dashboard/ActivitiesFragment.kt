package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormatSymbols
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentActivitiesBinding
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class ActivitiesFragment : Fragment() {
    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    private val months = DateFormatSymbols().months
    private val viewModel: ActivitiesViewModel by viewModels()
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val daynightTextColor = ResourcesCompat.getColor(resources, R.color.daynight_textColor, null)

        val endMillis = Calendar.getInstance().timeInMillis
        val startMillis = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis

        collectLatestWhenStarted(viewModel.offlineLogins) { logins ->
            val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
            renderChart(monthlyCounts, daynightTextColor)
        }
    }

    internal suspend fun computeMonthlyCounts(
        logins: List<OfflineActivity>,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, Int> = withContext(dispatcherProvider.default) {
        val calendar = Calendar.getInstance()
        logins.fold(mutableMapOf<Int, Int>()) { acc, activity ->
            val loginTime = activity.loginTime
            if (loginTime != null && loginTime in startMillis..endMillis) {
                calendar.timeInMillis = loginTime
                val month = calendar.get(Calendar.MONTH)
                acc[month] = (acc[month] ?: 0) + 1
            }
            acc
        }.toSortedMap()
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
