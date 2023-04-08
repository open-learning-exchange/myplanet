package org.ole.planet.myplanet.ui.enterprises


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kizitonwose.calendarview.model.*
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddMeetupBinding
import org.ole.planet.myplanet.databinding.FragmentEnterpriseCalendarBinding
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
    lateinit var binding: FragmentEnterpriseCalendarBinding

    lateinit var list: List<RealmMeetup>
    lateinit var start: Calendar
    lateinit var end: Calendar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEnterpriseCalendarBinding.inflate(inflater, container, false)
        start = Calendar.getInstance()
        end = Calendar.getInstance()
        var fab = binding.root.findViewById<View>(R.id.add_event)
        showHideFab(fab)
        binding.root.findViewById<View>(R.id.add_event)
            .setOnClickListener { view -> showMeetupAlert() }
//        rvCalendar = binding.root.findViewById(R.id.rv_calendar)
        return binding.root
    }

    private fun showHideFab(fab: View) {
        if (requireArguments().getBoolean("fromLogin", false)) {
            fab.visibility = View.GONE
        } else if (user != null) {
            if (user.isManager || user.isLeader)
                fab.visibility = View.VISIBLE
            else
                fab.visibility = View.GONE
        } else {
            fab.visibility = View.GONE

        }
    }

    private fun showMeetupAlert() {
        val mBinding: AddMeetupBinding = AddMeetupBinding.inflate(layoutInflater)
        setDatePickerListener(mBinding.tvStartDate, start)
        setDatePickerListener(mBinding.tvEndDate, end)
        setTimePicker(mBinding.tvStartTime)
        setTimePicker(mBinding.tvEndTime)

        AlertDialog.Builder(requireActivity()).setView(mBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val ttl = mBinding.etTitle.text.toString()
                val desc = mBinding.etDescription.text.toString()
                val loc = mBinding.etLocation.text.toString()
                if (ttl.isEmpty()) {
                    Utilities.toast(activity, "Title is required")
                } else if (desc.isEmpty()) {
                    Utilities.toast(activity, "Description is required")
                } else if (start == null) {
                    Utilities.toast(activity, "Start time is required")
                } else {
                    if (!mRealm.isInTransaction)
                        mRealm.beginTransaction()
                    val meetup =
                        mRealm.createObject(RealmMeetup::class.java, UUID.randomUUID().toString())
                    meetup.title = ttl
                    meetup.description = desc
                    meetup.meetupLocation = loc
                    meetup.creator = user.id
                    meetup.startDate = start.timeInMillis
                    if (end != null)
                        meetup.endDate = end.timeInMillis
                    meetup.endTime = mBinding.tvEndTime.text.toString()
                    meetup.startTime = mBinding.tvStartTime.text.toString()
                    val rb = mBinding.rgRecuring.checkedRadioButtonId
                    if (rb != null) {
                        meetup.recurring = rb.toString()
                    }
                    val ob = JsonObject()
                    ob.addProperty("teams", teamId)
                    meetup.links = Gson().toJson(ob)
                    meetup.teamId = teamId
                    mRealm.commitTransaction()
                    Utilities.toast(activity, "Meetup added")
                    binding.rvCalendar.adapter?.notifyDataSetChanged()
                    binding.calendarView.notifyCalendarChanged()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setDatePickerListener(view: TextView, date: Calendar?) {
        val c = Calendar.getInstance()
        view.setOnClickListener { v ->

            DatePickerDialog(requireActivity(), { vi, year, monthOfYear, dayOfMonth ->
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
            val timePickerDialog = TimePickerDialog(
                activity,
                { view, hourOfDay, minute ->
                    time.text = String.format("%02d:%02d", hourOfDay, minute)
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
            )
            timePickerDialog.show()
        }

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Utilities.log(teamId)
        list = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId)
            .greaterThanOrEqualTo("endDate", TimeUtils.currentDateLong()).findAll()
        binding.rvCalendar.layoutManager = LinearLayoutManager(activity)
        binding.rvCalendar.adapter = AdapterCalendar(activity, list)
        binding.calendarView.inDateStyle = InDateStyle.ALL_MONTHS
        binding.calendarView.outDateStyle = OutDateStyle.END_OF_ROW
        binding.calendarView.hasBoundaries = true
//        calendarView.dayWidth = 60
//        calendarView.dayHeight = 60
        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(10)
        val lastMonth = currentMonth.plusMonths(10)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)
        setUpCalendar()
        binding.calendarView.monthHeaderBinder =
            object : MonthHeaderFooterBinder<MonthViewContainer> {
                override fun create(view: View): MonthViewContainer {
                    val textView = view.findViewById<TextView>(R.id.calendarMonthText)
                    return MonthViewContainer(view, textView)
                }

                override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                    container.textView.setTextColor(activity!!.resources.getColor(R.color.colorPrimaryDark))
                    container.textView.text = "${
                        month.yearMonth.month.name.toLowerCase().capitalize()
                    } ${month.year}"
                }
            }
    }


    fun setUpCalendar() {
        binding.calendarView.dayBinder = object : DayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer {
                val textView = view.findViewById<TextView>(R.id.calendarDayText)
                return DayViewContainer(view, textView)
            }

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.setText(day.date.dayOfMonth.toString())
                var c = Calendar.getInstance()
                c.set(Calendar.YEAR, day.date.year)
                c.set(Calendar.MONTH, day.date.monthValue - 1)
                c.set(Calendar.DAY_OF_MONTH, day.date.dayOfMonth)
                var event = getEvent(c.timeInMillis)
                if (day.owner == DayOwner.THIS_MONTH) {
                    container.textView.setTextColor(Color.BLACK)
                } else {
                    container.textView.setTextColor(Color.GRAY)
                    container.textView.setTextSize(14.0f)
                }
                if (event != null) {
                    container.textView.setOnClickListener {
                        DialogUtils.showAlert(
                            activity!!,
                            event.title,
                            event.description
                        )
                    }
                    container.textView.setBackgroundColor(resources.getColor(R.color.colorPrimaryDark))
                    container.textView.setTextColor(resources.getColor(R.color.md_white_1000))
                }
            }
        }


    }

    private fun getEvent(time: Long): RealmMeetup? {
        for (realmMeetup in list) {
            if (time >= getTimeMills(realmMeetup.startDate, false) && time <= getTimeMills(
                    realmMeetup.endDate,
                    true
                )
            ) {
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

    class DayViewContainer(view: View, textView: TextView) : ViewContainer(view) {
        val textView = textView
    }

    class MonthViewContainer(view: View, textView: TextView) : ViewContainer(view) {
        val textView = textView
    }
}
