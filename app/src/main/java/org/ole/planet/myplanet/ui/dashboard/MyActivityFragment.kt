package org.ole.planet.myplanet.ui.dashboard

import java.text.DateFormatSymbols;

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_my_activity.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * A simple [Fragment] subclass.
 */
class MyActivityFragment : Fragment() {
    lateinit var realm: Realm;
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_activity, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var userModel = UserProfileDbHandler(activity!!).userModel
        realm = DatabaseService(activity!!).realmInstance
        var calendar = Calendar.getInstance()

        calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 1)
        var resourceActivity = realm.where(RealmOfflineActivity::class.java).equalTo("userId", userModel.id).between("loginTime", calendar.timeInMillis, Calendar.getInstance().timeInMillis).findAll()

        var countMap = HashMap<String, Int>();
        var format = SimpleDateFormat("MMM")
        resourceActivity.forEach {

            var d = format.format(it.loginTime)
            Utilities.log(d)
            if (countMap.containsKey(d)) {
                countMap[d] = countMap[d]!!.plus(1)
            } else {
                countMap[d] = 1
            }
        }
        Utilities.log("${resourceActivity.size} size map : ${countMap.size} ")
        var entries = ArrayList<BarEntry>()
        var i = 0;
        for (entry in countMap.keys) {
            var key = format.parse(entry)
            var en = BarEntry( key.month.toFloat(), countMap[entry]!!.toFloat())
            entries.add(en)
            i = i.plus(1)
        }
        var e = Gson().toJson(countMap)
        Utilities.log("$e")
        Utilities.log("${entries.size} size")
        val dataSet = BarDataSet(entries, "No of login ")

        val lineData = BarData(dataSet)
        chart.setData(lineData)
        var d = Description()
        d.text = "Login Activity chart"
        chart.description = d
        chart.xAxis.valueFormatter = object: ValueFormatter(){
            override fun getFormattedValue(value: Float): String {
                Utilities.log("value ${value.toInt()}")
                return getMonth(value.toInt())
            }
        }
        chart.invalidate()


    }

    public fun  getMonth( month: Int) : String {
        return  DateFormatSymbols().months[month];
    }
}
