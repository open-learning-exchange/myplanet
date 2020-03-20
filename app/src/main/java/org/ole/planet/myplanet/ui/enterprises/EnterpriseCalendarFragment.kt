package org.ole.planet.myplanet.ui.enterprises


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kizitonwose.calendarview.model.*
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import kotlinx.android.synthetic.main.calendar_day.view.*
import kotlinx.android.synthetic.main.calendar_month.view.*
import kotlinx.android.synthetic.main.fragment_enterprise_calendar.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.threeten.bp.YearMonth
import org.threeten.bp.temporal.WeekFields
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class EnterpriseCalendarFragment : BaseTeamFragment() {

    lateinit var list: List<RealmMeetup>
    lateinit var startDate: TextView
    lateinit var startTime: TextView
    lateinit var endDate: TextView
    lateinit var endTime: TextView
    lateinit var start: Calendar
    lateinit var end: Calendar
    lateinit var rvCalendar: RecyclerView


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_enterprise_calendar, container, false)
        start = Calendar.getInstance()
        end = Calendar.getInstance()
        var fab = v.findViewById<View>(R.id.add_event)
        showHideFab(fab)
        v.findViewById<View>(R.id.add_event).setOnClickListener { view -> showMeetupAlert() }
        rvCalendar = v.findViewById(R.id.rv_calendar)
        return v
    }

    private fun showHideFab(fab: View) {
         if (arguments!!.getBoolean("fromLogin", false)){
             fab.visibility = View.GONE
         }  else if(user!=null){
            if(user.isManager || user.isLeader)
                fab.visibility = View.VISIBLE
            else
                fab.visibility = View.GONE
        }else{
            fab.visibility = View.GONE

        }
    }


    private fun showMeetupAlert() {
        val v = LayoutInflater.from(activity).inflate(R.layout.add_meetup, null)
        val title = v.findViewById<TextView>(R.id.et_title)
        val location = v.findViewById<TextView>(R.id.et_location)
        val description = v.findViewById<TextView>(R.id.et_description)
        val radioGroup = v.findViewById<RadioGroup>(R.id.rg_recuring)
        startDate = v.findViewById(R.id.tv_start_date)
        startTime = v.findViewById(R.id.tv_start_time)
        endDate = v.findViewById(R.id.tv_end_date)
        endTime = v.findViewById(R.id.tv_end_time)
        setDatePickerListener(startDate, start)
        setDatePickerListener(endDate, end)
        setTimePicker(startTime)
        setTimePicker(endTime)

        AlertDialog.Builder(activity!!).setView(v)
                .setPositiveButton("Save") { dialogInterface, i ->
                    val ttl = title.text.toString()
                    val desc = description.text.toString()
                    val loc = location.text.toString()
                    if (ttl.isEmpty()) {
                        Utilities.toast(activity, "Title is required")
                    } else if (desc.isEmpty()) {
                        Utilities.toast(activity, "Description is required")
                    } else if (start == null) {
                        Utilities.toast(activity, "Start time is required")
                    } else {
                        if (!mRealm.isInTransaction)
                            mRealm.beginTransaction()
                        val meetup = mRealm.createObject(RealmMeetup::class.java, UUID.randomUUID().toString())
                        meetup.title = ttl
                        meetup.description = desc
                        meetup.meetupLocation = loc
                        meetup.creator = user.id
                        meetup.startDate = start!!.timeInMillis
                        if (end != null)
                            meetup.endDate = end!!.timeInMillis
                        meetup.endTime = endTime.text.toString()
                        meetup.startTime = startTime.text.toString()
                        val rb = v.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
                        if (rb != null) {
                            meetup.recurring = rb.text.toString()
                        }
                        val ob = JsonObject()
                        ob.addProperty("teams", teamId)
                        meetup.links = Gson().toJson(ob)
                        meetup.teamId = teamId
                        mRealm.commitTransaction()
                        Utilities.toast(activity, "Meetup added")
                        rvCalendar.adapter?.notifyDataSetChanged()
                        calendarView.notifyCalendarChanged()
                    }
                }.setNegativeButton("Cancel", null).show()
    }


    private fun setDatePickerListener(view: TextView, date: Calendar?) {
        val c = Calendar.getInstance()
        view.setOnClickListener { v ->

            DatePickerDialog(activity!!, { vi, year, monthOfYear, dayOfMonth ->
                date!!.set(Calendar.YEAR, year)
                date.set(Calendar.MONTH, monthOfYear)
                date.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                if (view != null)
                    view.text = TimeUtils.formatDate(date.timeInMillis, "yyyy-MM-dd")
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()

        }
    }


    private fun setTimePicker(time: TextView) {
        val c = Calendar.getInstance()
        time.setOnClickListener { v ->
            val timePickerDialog = TimePickerDialog(activity,
                    { view, hourOfDay, minute -> time.text = String.format("%02d:%02d", hourOfDay, minute) }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
            timePickerDialog.show()
        }

    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Utilities.log(teamId)
        list = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId).greaterThanOrEqualTo("endDate", TimeUtils.currentDateLong()).findAll()
        rvCalendar.layoutManager = LinearLayoutManager(activity)
        rvCalendar.adapter = AdapterCalendar(activity, list)
        calendarView.inDateStyle = InDateStyle.ALL_MONTHS
        calendarView.outDateStyle = OutDateStyle.END_OF_ROW
        calendarView.hasBoundaries = true
//        calendarView.dayWidth = 60
//        calendarView.dayHeight = 60
        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(10)
        val lastMonth = currentMonth.plusMonths(10)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        calendarView.scrollToMonth(currentMonth)
        setUpCalendar()
        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                container.textView.setTextColor(activity!!.resources.getColor(R.color.colorPrimaryDark))
                container.textView.text = "${month.yearMonth.month.name.toLowerCase().capitalize()} ${month.year}"
            }
        }
    }


    fun setUpCalendar() {
        calendarView.dayBinder = object : DayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()
                var c = Calendar.getInstance()
                c.set(Calendar.YEAR, day.date.year)
                c.set(Calendar.MONTH, day.date.monthValue - 1)
                c.set(Calendar.DAY_OF_MONTH, day.date.dayOfMonth)
                var event = getEvent(c.timeInMillis)
                if (day.owner == DayOwner.THIS_MONTH) {
                    container.textView.setTextColor(Color.BLACK)
                } else {
                    container.textView.setTextColor(Color.GRAY)
                    container.textView.textSize = 14.0f
                }
                if (event != null) {
                    container.textView.setOnClickListener { DialogUtils.showAlert(activity!!, event.title, event.description) }
                    container.textView.setBackgroundColor(resources.getColor(R.color.colorPrimaryDark))
                    container.textView.setTextColor(resources.getColor(R.color.md_white_1000))
                }
            }
        }


    }

    private fun getEvent(time: Long): RealmMeetup? {
        for (realmMeetup in list) {
            if (time >= getTimeMills(realmMeetup.startDate, false) && time <= getTimeMills(realmMeetup.endDate, true)) {
                return realmMeetup
            }
        }
        return null
    }

    private fun getTimeMills(time: Long, end: Boolean): Long {
        var c = Calendar.getInstance()
        c.timeInMillis = time
        c.set(Calendar.MINUTE, if (end) 59 else 0)
        c.set(Calendar.HOUR, if (end) 23 else 0)
        c.set(Calendar.SECOND, if (end) 59 else 0)
        return c.timeInMillis;
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView = view.calendarDayText
    }

    class MonthViewContainer(view: View) : ViewContainer(view) {
        val textView = view.calendarMonthText
    }

}
