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
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMyActivityBinding
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler

@AndroidEntryPoint
class MyActivityFragment : Fragment() {
    private var _binding: FragmentMyActivityBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var userRepository: UserRepository
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userModel = userProfileDbHandler.userModel
        val daynightTextColor = ResourcesCompat.getColor(resources, R.color.daynight_textColor, null)

        val endMillis = Calendar.getInstance().timeInMillis
        val startMillis = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis

        val userId = userModel?.id ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val monthlyCounts = userRepository.getMonthlyLoginCounts(userId, startMillis, endMillis)
            renderChart(monthlyCounts, daynightTextColor)
        }
    }

    private fun renderChart(monthlyCounts: Map<Int, Int>, textColor: Int) {
        val entries = monthlyCounts.entries
            .sortedBy { it.key }
            .map { (month, count) -> BarEntry(month.toFloat(), count.toFloat()) }

        val label = getString(R.string.chart_label)
        val dataSet = BarDataSet(entries, label)
        val barData = BarData(dataSet)

        val description = Description().apply {
            text = getString(R.string.chart_description)
            textColor = textColor
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
