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
import io.realm.Realm
import org.ole.planet.myplanet.databinding.FragmentMyActivityBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.service.UserProfileDbHandler
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import org.ole.planet.myplanet.R

class MyActivityFragment : Fragment() {
    private lateinit var fragmentMyActivityBinding : FragmentMyActivityBinding
    lateinit var realm: Realm
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyActivityBinding = FragmentMyActivityBinding.inflate(inflater, container, false)
        return fragmentMyActivityBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userModel = UserProfileDbHandler(requireActivity()).userModel
        realm = DatabaseService(requireActivity()).realmInstance
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

        val dataSet = BarDataSet(entries, "No of login ")

        val lineData = BarData(dataSet)
        fragmentMyActivityBinding.chart.data = lineData
        val d = Description()
        d.text = "Login Activity chart"
        d.textColor = daynight_textColor
        fragmentMyActivityBinding.chart.description = d
        fragmentMyActivityBinding.chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return getMonth(value.toInt())
            }
        }
        fragmentMyActivityBinding.chart.xAxis.textColor = daynight_textColor
        fragmentMyActivityBinding.chart.axisLeft.textColor = daynight_textColor
        fragmentMyActivityBinding.chart.axisRight.textColor = daynight_textColor
        fragmentMyActivityBinding.chart.legend.textColor = daynight_textColor
        fragmentMyActivityBinding.chart.invalidate()
    }

    fun getMonth(month: Int): String {
        return DateFormatSymbols().months[month]
    }
}
