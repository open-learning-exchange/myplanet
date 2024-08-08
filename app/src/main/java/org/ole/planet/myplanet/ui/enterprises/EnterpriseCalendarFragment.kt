package org.ole.planet.myplanet.ui.enterprises

import android.app.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.google.gson.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.mymeetup.AdapterMeetup
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.*
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class EnterpriseCalendarFragment : BaseTeamFragment() {
    private lateinit var fragmentEnterpriseCalendarBinding: FragmentEnterpriseCalendarBinding
    private val selectedDates: MutableList<Calendar> = mutableListOf()
    private lateinit var calendar: CalendarView
    private lateinit var list: List<Calendar>
    private lateinit var start: Calendar
    private lateinit var end: Calendar
    private lateinit var calendarEventsMap: MutableMap<CalendarDay, RealmMeetup>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentEnterpriseCalendarBinding = FragmentEnterpriseCalendarBinding.inflate(inflater, container, false)
        start = Calendar.getInstance()
        end = Calendar.getInstance()
        if (!isMember()) {
            fragmentEnterpriseCalendarBinding.addEvent.visibility = View.GONE
        }
        fragmentEnterpriseCalendarBinding.addEvent.setOnClickListener { showMeetupAlert() }
        return fragmentEnterpriseCalendarBinding.root
    }

    private fun showMeetupAlert() {
        val addMeetupBinding = AddMeetupBinding.inflate(layoutInflater)
        setDatePickerListener(addMeetupBinding.tvStartDate, start)
        setDatePickerListener(addMeetupBinding.tvEndDate, end)
        setTimePicker(addMeetupBinding.tvStartTime)
        setTimePicker(addMeetupBinding.tvEndTime)

        val alertDialog = AlertDialog.Builder(requireActivity()).setView(addMeetupBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val ttl = "${addMeetupBinding.etTitle.text}"
                val desc = "${addMeetupBinding.etDescription.text}"
                val loc = "${addMeetupBinding.etLocation.text}"
                if (ttl.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.title_is_required))
                } else if (desc.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.description_is_required))
                } else {
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    val meetup = mRealm.createObject(RealmMeetup::class.java, "${UUID.randomUUID()}")
                    meetup.title = ttl
                    meetup.description = desc
                    meetup.meetupLocation = loc
                    meetup.creator = user?.name
                    meetup.startDate = start.timeInMillis
                    meetup.endDate = end.timeInMillis
                    meetup.endTime = "${addMeetupBinding.tvEndTime.text}"
                    meetup.startTime = "${addMeetupBinding.tvStartTime.text}"
                    val rb = addMeetupBinding.rgRecuring.findViewById<RadioButton>(addMeetupBinding.rgRecuring.checkedRadioButtonId)
                    if (rb != null) {
                        meetup.recurring = "${rb.text}"
                    }
                    val ob = JsonObject()
                    ob.addProperty("teams", teamId)
                    meetup.links = Gson().toJson(ob)
                    meetup.teamId = teamId
                    mRealm.commitTransaction()
                    Utilities.toast(activity, getString(R.string.meetup_added))
                    refreshCalendarView()
                }
            }.setNegativeButton("Cancel", null).show()
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
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
                    time.text = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute) },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
            timePickerDialog.show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = mutableListOf()
        calendar = fragmentEnterpriseCalendarBinding.calendarView
        calendarEventsMap = mutableMapOf()
        fragmentEnterpriseCalendarBinding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val clickedCalendar = calendarDay.calendar
                val clickedDateInMillis = clickedCalendar.timeInMillis

                val meetupList = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId).findAll()
                val isDateMarked = meetupList.any { meetup ->
                    meetup.startDate == clickedDateInMillis
                }

                if (isDateMarked) {
                    showMeetupDetails(clickedDateInMillis)
                    showHideFab()
                } else {
                    if (arguments?.getBoolean("fromLogin", false) != true && arguments?.getBoolean("fromCommunity", false) == true) {
                        showMeetupAlert()
                    }
                }
                if (!selectedDates.contains(clickedCalendar)) {
                    selectedDates.add(clickedCalendar)
                } else {
                    selectedDates.remove(clickedCalendar)
                }
            }
        })

        refreshCalendarView()
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    private fun showMeetupDetails(dateInMillis: Long) {
        fragmentEnterpriseCalendarBinding.meetup.visibility = View.VISIBLE

        val meetupList = mRealm.where(RealmMeetup::class.java).equalTo("startDate", dateInMillis).findAll()
        fragmentEnterpriseCalendarBinding.rvMeetups.layoutManager = LinearLayoutManager(requireContext())
        fragmentEnterpriseCalendarBinding.rvMeetups.adapter = AdapterMeetup(meetupList)
    }

    private fun refreshCalendarView() {
        val meetupList = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId).findAll()
        val eventDates: MutableList<Calendar> = mutableListOf()
        val calendarInstance = Calendar.getInstance()

        for (meetup in meetupList) {
            val startDateMillis = meetup.startDate
            calendarInstance.timeInMillis = startDateMillis
            eventDates.add(calendarInstance.clone() as Calendar)
        }

        fragmentEnterpriseCalendarBinding.calendarView.selectedDates = eventDates
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
}