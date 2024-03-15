package org.ole.planet.myplanet.ui.enterprises

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kizitonwose.calendarview.model.*
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddMeetupBinding
import org.ole.planet.myplanet.databinding.CalendarDayBinding
import org.ole.planet.myplanet.databinding.CalendarMonthBinding
import org.ole.planet.myplanet.databinding.FragmentEnterpriseCalendarBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class EnterpriseCalendarFragment : BaseTeamFragment() {
    private lateinit var fragmentEnterpriseCalendarBinding: FragmentEnterpriseCalendarBinding
    lateinit var list: List<RealmMeetup>
    lateinit var start: Calendar
    lateinit var end: Calendar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentEnterpriseCalendarBinding = FragmentEnterpriseCalendarBinding.inflate(inflater, container, false)
        start = Calendar.getInstance()
        end = Calendar.getInstance()
        showHideFab()
        fragmentEnterpriseCalendarBinding.addEvent.setOnClickListener { showMeetupAlert() }
        return fragmentEnterpriseCalendarBinding.root
    }

    private fun showHideFab() {
        if (requireArguments().getBoolean("fromLogin", false)) {
            fragmentEnterpriseCalendarBinding.addEvent.visibility = View.GONE
        } else if (user != null) {
            if (user?.isManager() == true || user?.isLeader() == true) {
                fragmentEnterpriseCalendarBinding.addEvent.visibility = View.VISIBLE
            } else {
                fragmentEnterpriseCalendarBinding.addEvent.visibility = View.GONE
            }
        } else {
            fragmentEnterpriseCalendarBinding.addEvent.visibility = View.GONE
        }
    }

    private fun showMeetupAlert() {
        val addMeetupBinding = AddMeetupBinding.inflate(layoutInflater)
        setDatePickerListener(addMeetupBinding.tvStartDate, start)
        setDatePickerListener(addMeetupBinding.tvEndDate, end)
        setTimePicker(addMeetupBinding.tvStartTime)
        setTimePicker(addMeetupBinding.tvEndTime)

        AlertDialog.Builder(requireActivity()).setView(addMeetupBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val ttl = addMeetupBinding.etTitle.text.toString()
                val desc = addMeetupBinding.etDescription.text.toString()
                val loc = addMeetupBinding.etLocation.text.toString()
                if (ttl.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.title_is_required))
                } else if (desc.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.description_is_required))
                } else if (start == null) {
                    Utilities.toast(activity, getString(R.string.start_time_is_required))
                } else {
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    val meetup = mRealm.createObject(RealmMeetup::class.java, UUID.randomUUID().toString())
                    meetup.title = ttl
                    meetup.description = desc
                    meetup.meetupLocation = loc
                    meetup.creator = user?.id
                    meetup.startDate = start.timeInMillis
                    if (end != null) meetup.endDate = end.timeInMillis
                    meetup.endTime = addMeetupBinding.tvEndTime.text.toString()
                    meetup.startTime = addMeetupBinding.tvStartTime.text.toString()
                    val rb = addMeetupBinding.rgRecuring.findViewById<RadioButton>(addMeetupBinding.rgRecuring.checkedRadioButtonId)
                    if (rb != null) {
                        meetup.recurring = rb.text.toString()
                    }
                    val ob = JsonObject()
                    ob.addProperty("teams", teamId)
                    meetup.links = Gson().toJson(ob)
                    meetup.teamId = teamId
                    mRealm.commitTransaction()
                    Utilities.toast(activity, getString(R.string.meetup_added))
                    fragmentEnterpriseCalendarBinding.rvCalendar.adapter?.notifyDataSetChanged()
                    fragmentEnterpriseCalendarBinding.calendarView.notifyCalendarChanged()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setDatePickerListener(view: TextView, date: Calendar?) {
        val c = Calendar.getInstance()
        view.setOnClickListener {

            DatePickerDialog(requireActivity(), { _, year, monthOfYear, dayOfMonth ->
                date?.set(Calendar.YEAR, year)
                date?.set(Calendar.MONTH, monthOfYear)
                date?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                view.text = date?.timeInMillis?.let { it1 -> TimeUtils.formatDate(it1, "yyyy-MM-dd") }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()

        }
    }

    private fun setTimePicker(time: TextView) {
        val c = Calendar.getInstance()
        time.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                activity, { _, hourOfDay, minute ->
                    time.text = String.format("%02d:%02d", hourOfDay, minute) },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
            timePickerDialog.show()
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Utilities.log(teamId)
        list = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId).greaterThanOrEqualTo("endDate", TimeUtils.currentDateLong()).findAll()
        fragmentEnterpriseCalendarBinding.rvCalendar.layoutManager = LinearLayoutManager(activity)
        fragmentEnterpriseCalendarBinding.rvCalendar.adapter = AdapterCalendar(list)
        fragmentEnterpriseCalendarBinding.calendarView.inDateStyle = InDateStyle.ALL_MONTHS
        fragmentEnterpriseCalendarBinding.calendarView.outDateStyle = OutDateStyle.END_OF_ROW
        fragmentEnterpriseCalendarBinding.calendarView.hasBoundaries = true
        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(10)
        val lastMonth = currentMonth.plusMonths(10)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        fragmentEnterpriseCalendarBinding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        fragmentEnterpriseCalendarBinding.calendarView.scrollToMonth(currentMonth)
        setUpCalendar()
        fragmentEnterpriseCalendarBinding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                container.textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark))
                container.textView.text = "${month.yearMonth.month.name.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(Locale.ROOT)
                    } else {
                        it.toString()
                    } }} ${month.year}"
            }
        }
    }

    private fun setUpCalendar() {

        fragmentEnterpriseCalendarBinding.calendarView.dayBinder = object : DayBinder<DayViewContainer> {
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
                    container.textView.setOnClickListener {
                        DialogUtils.showAlert(context, event.title, event.description)
                    }
                    container.textView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark))
                    container.textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                }
            }
        }
    }

    private fun getEvent(time: Long): RealmMeetup? {
        for (realmMeetup in list) {
            if (time >= getTimeMills(realmMeetup.startDate, false)
                && time <= getTimeMills(realmMeetup.endDate, true)) {
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
        val textView = CalendarDayBinding.bind(view).calendarDayText
    }

    class MonthViewContainer(view: View) : ViewContainer(view) {
        val textView = CalendarMonthBinding.bind(view).calendarMonthText
    }
}