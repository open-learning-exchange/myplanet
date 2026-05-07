package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormatSymbols
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentActivitiesBinding
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.services.UserSessionManager

@AndroidEntryPoint
class ActivitiesFragment : Fragment() {
    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var activitiesRepository: ActivitiesRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val daynightTextColor = ResourcesCompat.getColor(resources, R.color.daynight_textColor, null)

        val endMillis = Calendar.getInstance().timeInMillis
        val startMillis = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis

        viewLifecycleOwner.lifecycleScope.launch {
            val userName = userSessionManager.getUserModel()?.name ?: return@launch
            activitiesRepository.getOfflineLogins(userName).collectLatest { logins ->
                val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
                renderChart(monthlyCounts, daynightTextColor)
            }
        }
    }

    private fun computeMonthlyCounts(
        logins: List<RealmOfflineActivity>,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, Int> {
        val calendar = Calendar.getInstance()
        return logins
            .mapNotNull { it.loginTime }
            .filter { it in startMillis..endMillis }
            .map { loginTime ->
                calendar.timeInMillis = loginTime
                calendar.get(Calendar.MONTH)
            }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
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

        val description = Description().apply {
            text = getString(R.string.chart_description)
            setTextColor(textColor)
        }

        binding.chart.apply {
            data = barData
            this.description = description
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return getMonth(value.toInt())
                }
            }
            xAxis.textColor = textColor
            axisLeft.textColor = textColor
            axisRight.textColor = textColor
            legend.textColor = textColor
            this.description.setPosition(850f, 830f)
            this.data.setValueTextColor(textColor)
            invalidate()
        }
    }

    fun getMonth(month: Int): String {
        return DateFormatSymbols().months[month]
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
