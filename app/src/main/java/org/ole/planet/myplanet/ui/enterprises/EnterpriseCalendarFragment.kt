package org.ole.planet.myplanet.ui.enterprises

import android.app.*
import android.content.Context
import android.content.res.Resources
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.google.gson.*
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.ui.mymeetup.AdapterMeetup
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*

class EnterpriseCalendarFragment : BaseTeamFragment() {
    private lateinit var fragmentEnterpriseCalendarBinding: FragmentEnterpriseCalendarBinding
    private val selectedDates: MutableList<Calendar> = mutableListOf()
    private lateinit var calendar: CalendarView
    private lateinit var list: List<Calendar>
    private lateinit var start: Calendar
    private lateinit var end: Calendar
    private lateinit var clickedCalendar: Calendar
    private lateinit var calendarEventsMap: MutableMap<CalendarDay, RealmMeetup>
    private lateinit var meetupList: RealmResults<RealmMeetup>

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
        setDatePickerListener(addMeetupBinding.tvStartDate, start, end)
        setDatePickerListener(addMeetupBinding.tvEndDate, end, null)
        setTimePicker(addMeetupBinding.tvStartTime)
        setTimePicker(addMeetupBinding.tvEndTime)
        if (!::clickedCalendar.isInitialized) {
            clickedCalendar = Calendar.getInstance()
        }

        val alertDialog = AlertDialog.Builder(requireActivity()).setView(addMeetupBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val title = "${addMeetupBinding.etTitle.text}"
                val description = "${addMeetupBinding.etDescription.text}"
                val location = "${addMeetupBinding.etLocation.text}"
                if (title.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.title_is_required))
                } else if (description.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.description_is_required))
                } else {
                    try {
                        if (!mRealm.isInTransaction) {
                            mRealm.beginTransaction()
                        }
                        val meetup = mRealm.createObject(RealmMeetup::class.java, "${UUID.randomUUID()}")
                        meetup.title = title
                        meetup.description = description
                        meetup.meetupLocation = location
                        meetup.creator = user?.name
                        meetup.startDate = start.timeInMillis
                        meetup.endDate = end.timeInMillis
                        meetup.endTime = "${addMeetupBinding.tvEndTime.text}"
                        meetup.startTime = "${addMeetupBinding.tvStartTime.text}"
                        meetup.createdDate = System.currentTimeMillis()
                        meetup.sourcePlanet = team?.teamPlanetCode
                        val jo = JsonObject()
                        jo.addProperty("type", "local")
                        jo.addProperty("planetCode", team?.teamPlanetCode)
                        meetup.sync = Gson().toJson(jo)
                        val rb = addMeetupBinding.rgRecuring.findViewById<RadioButton>(addMeetupBinding.rgRecuring.checkedRadioButtonId)
                        if (rb != null) {
                            meetup.recurring = "${rb.text}"
                        }
                        val ob = JsonObject()
                        ob.addProperty("teams", teamId)
                        meetup.link = Gson().toJson(ob)
                        meetup.teamId = teamId
                        mRealm.commitTransaction()
                        Utilities.toast(activity, getString(R.string.meetup_added))
                        refreshCalendarView()
                    } catch (e: Exception) {
                        mRealm.cancelTransaction()
                        e.printStackTrace()
                        Utilities.toast(activity, getString(R.string.meetup_not_added))
                    }
                }
            }.setNegativeButton("Cancel", null).create()

        alertDialog.setOnDismissListener {
            if (selectedDates.contains(clickedCalendar)) {
                selectedDates.remove(clickedCalendar)
                refreshCalendarView()
            }
        }
        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun setDatePickerListener(view: TextView, date: Calendar?, endDate: Calendar?) {
        val c = Calendar.getInstance()
        if (date != null && endDate != null) {
            view.text = date.timeInMillis.let { it1 -> TimeUtils.formatDate(it1, "yyyy-MM-dd") }
        }
        view.setOnClickListener {
            DatePickerDialog(requireActivity(), { _, year, monthOfYear, dayOfMonth ->
                date?.set(Calendar.YEAR, year)
                date?.set(Calendar.MONTH, monthOfYear)
                date?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                if(endDate != null && date == Calendar.getInstance()){
                    endDate.set(Calendar.YEAR, year)
                    endDate.set(Calendar.MONTH, monthOfYear)
                    endDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
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

    override fun onResume() {
        super.onResume()
        setupCalendarClickListener()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = mutableListOf()
        calendar = fragmentEnterpriseCalendarBinding.calendarView
        calendarEventsMap = mutableMapOf()
        setupCalendarClickListener()
    }

    private fun setupCalendarClickListener(){
        fragmentEnterpriseCalendarBinding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                meetupList = mRealm.where(RealmMeetup::class.java).equalTo("teamId", teamId).findAll()
                clickedCalendar = calendarDay.calendar
                val clickedDateInMillis = clickedCalendar.timeInMillis
                val clickedDate = Instant.ofEpochMilli(clickedDateInMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                val markedDates = meetupList.mapNotNull { meetup ->
                    val meetupDate = Instant.ofEpochMilli(meetup.startDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    if (meetupDate == clickedDate) meetup else null
                }

                if (markedDates.isNotEmpty()) {
                    showMeetupDialog(markedDates)
                    showHideFab()
                } else {
                    start = clickedCalendar
                    end = clickedCalendar
                    showMeetupAlert()
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
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun getCardViewHeight(context: Context): Int {
        val view = LayoutInflater.from(context).inflate(R.layout.item_meetup, null)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(Resources.getSystem().displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return view.measuredHeight
    }

    private fun showMeetupDialog(meetupList: List<RealmMeetup>) {
        println(meetupList.size)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.meetup_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvMeetups)
        val dialogTitle = dialogView.findViewById< TextView>(R.id.tvTitle)
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        dialogTitle.text = dateFormat.format(clickedCalendar.time)
        val cardHeight = getCardViewHeight(requireContext())
        recyclerView.layoutParams.height = cardHeight
        recyclerView.requestLayout()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = AdapterMeetup(meetupList)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnadd).setOnClickListener {
            start = clickedCalendar
            end = clickedCalendar
            showMeetupAlert()
        }

        dialog.show()
    }

    private fun refreshCalendarView() {
        if (teamId.isEmpty()) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var meetupList = mutableListOf<RealmMeetup>()
            val eventDates: MutableList<Calendar> = mutableListOf()
            val realm = Realm.getDefaultInstance()
            try {
                meetupList = realm.where(RealmMeetup::class.java).equalTo("teamId", teamId).findAll()
                val calendarInstance = Calendar.getInstance()

                for (meetup in meetupList) {
                    val startDateMillis = meetup.startDate
                    calendarInstance.timeInMillis = startDateMillis
                    eventDates.add(calendarInstance.clone() as Calendar)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                realm.close()
            }
            withContext(Dispatchers.Main) {
                if (isAdded && activity != null) {
                    fragmentEnterpriseCalendarBinding.calendarView.selectedDates = eventDates
                }
            }
        }
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
