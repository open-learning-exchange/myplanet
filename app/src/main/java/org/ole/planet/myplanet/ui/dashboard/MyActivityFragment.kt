package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMyActivityBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.service.UserProfileDbHandler

@AndroidEntryPoint
class MyActivityFragment : Fragment() {
    private var _binding: FragmentMyActivityBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    lateinit var realm: Realm
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userModel = UserProfileDbHandler(requireActivity()).userModel
        realm = databaseService.realmInstance
        val calendar = Calendar.getInstance()
        val daynight_textColor = ResourcesCompat.getColor(getResources(), R.color.daynight_textColor, null);

        calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 1)
        val resourceActivity = realm.where(RealmOfflineActivity::class.java).equalTo("userId", userModel?.id)
            .between("loginTime", calendar.timeInMillis, Calendar.getInstance().timeInMillis)
            .findAll()

        val countMap = HashMap<String, Int>()
        val format = SimpleDateFormat("MMM")
        resourceActivity.forEach {
            val d = format.format(it.loginTime)
            if (countMap.containsKey(d)) {
                countMap[d] = countMap[d]!!.plus(1)
            } else {
                countMap[d] = 1
            }
        }
        val entries = ArrayList<BarEntry>()
        var i = 0
        for (entry in countMap.keys) {
            val key = format.parse(entry)
            val calendar = Calendar.getInstance()
            key?.let {
                calendar.time = it
                val month = calendar.get(Calendar.MONTH)
                val en = countMap[entry]?.toFloat()
                    ?.let { it1 -> BarEntry(month.toFloat(), it1) }
                if (en != null) {
                    entries.add(en)
                }
            }
            i = i.plus(1)
        }
        var label = getString(R.string.chart_label)
        val dataSet = BarDataSet(entries, label)

        val lineData = BarData(dataSet)
        binding.chart.data = lineData
        val d = Description()
        d.text = getString(R.string.chart_description)
        d.textColor = daynight_textColor
        binding.chart.description = d
        binding.chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return getMonth(value.toInt())
            }
        }
        binding.chart.xAxis.textColor = daynight_textColor
        binding.chart.axisLeft.textColor = daynight_textColor
        binding.chart.axisRight.textColor = daynight_textColor
        binding.chart.legend.textColor = daynight_textColor
        binding.chart.description.setPosition(850f,830f)
        binding.chart.data.setValueTextColor(daynight_textColor)
        binding.chart.invalidate()
    }

    fun getMonth(month: Int): String {
        return DateFormatSymbols().months[month]
    }

    override fun onDestroyView() {
        if (::realm.isInitialized && !realm.isClosed) {
            realm.close()
        }
        _binding = null
        super.onDestroyView()
    }
}
