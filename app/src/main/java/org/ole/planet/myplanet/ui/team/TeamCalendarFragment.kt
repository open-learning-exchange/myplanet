package org.ole.planet.myplanet.ui.team

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.net.MalformedURLException
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddMeetupBinding
import org.ole.planet.myplanet.databinding.FragmentEnterpriseCalendarBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.MeetupRepository
import org.ole.planet.myplanet.ui.mymeetup.AdapterMeetup
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamCalendarFragment : BaseTeamFragment() {
    private var _binding: FragmentEnterpriseCalendarBinding? = null
    private val binding get() = _binding!!
    private val selectedDates: MutableList<Calendar> = mutableListOf()
    private lateinit var calendar: CalendarView
    private lateinit var list: List<Calendar>
    private lateinit var start: Calendar
    private lateinit var end: Calendar
    private lateinit var clickedCalendar: Calendar
    private lateinit var calendarEventsMap: MutableMap<CalendarDay, RealmMeetup>
    private var meetupList: List<RealmMeetup> = emptyList()
    private val eventDates: MutableList<Calendar> = mutableListOf()
    private var addMeetupDialog: AlertDialog? = null
    private var meetupDialog: AlertDialog? = null
    private var meetupAdapter: AdapterMeetup? = null
    @Inject
    lateinit var meetupRepository: MeetupRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEnterpriseCalendarBinding.inflate(inflater, container, false)
        start = Calendar.getInstance()
        end = Calendar.getInstance()
        return binding.root
    }

    fun String.isValidWebLink(): Boolean {
        val toCheck = when {
            startsWith("http://",  ignoreCase = true) || startsWith("https://", ignoreCase = true) -> this
            else -> "http://$this"
        }
        return try {
            val url = URL(toCheck)
            url.host.contains(".")
        } catch (e: MalformedURLException) {
            false
        }
    }

    private fun showMeetupAlert() {
        if (addMeetupDialog?.isShowing == true) return
        val addMeetupBinding = AddMeetupBinding.inflate(layoutInflater)
        setDatePickerListener(addMeetupBinding.tvStartDate, start, end)
        setDatePickerListener(addMeetupBinding.tvEndDate, end, null)
        setTimePicker(addMeetupBinding.tvStartTime)
        setTimePicker(addMeetupBinding.tvEndTime)
        if (!::clickedCalendar.isInitialized) {
            clickedCalendar = Calendar.getInstance()
        }
        addMeetupDialog = AlertDialog.Builder(requireActivity()).setView(addMeetupBinding.root).create()
        addMeetupBinding.btnSave.setOnClickListener {
            val title = "${addMeetupBinding.etTitle.text.trim()}"
            val link = "${addMeetupBinding.etLink.text.trim()}"
            val description = "${addMeetupBinding.etDescription.text.trim()}"
            val location = "${addMeetupBinding.etLocation.text.trim()}"
            if (title.isEmpty()) {
                Utilities.toast(activity, getString(R.string.title_is_required))
            } else if (description.isEmpty()) {
                Utilities.toast(activity, getString(R.string.description_is_required))
            } else if (!link.isValidWebLink() && link.isNotEmpty()) {
                Utilities.toast(activity, getString(R.string.invalid_url))
            } else {
                val defaultPlaceholder = getString(R.string.click_here_to_pick_time)
                val startTimeText = "${addMeetupBinding.tvStartTime.text}"
                val endTimeText = "${addMeetupBinding.tvEndTime.text}"
                val recurringId = addMeetupBinding.rgRecuring.checkedRadioButtonId
                val rb = addMeetupBinding.rgRecuring.findViewById<RadioButton>(recurringId)
                val recurringText = rb?.text?.toString()
                val teamPlanetCode = team?.teamPlanetCode
                val userName = user?.name
                val startMillis = start.timeInMillis
                val endMillis = end.timeInMillis
                val currentTeamId = teamId

                lifecycleScope.launch {
                    val meetup = RealmMeetup().apply {
                        id = "${UUID.randomUUID()}"
                        this.title = title
                        meetupLink = link
                        this.description = description
                        meetupLocation = location
                        creator = userName
                        startDate = startMillis
                        endDate = endMillis
                        if (startTimeText == defaultPlaceholder) {
                            startTime = ""
                        } else {
                            startTime = startTimeText
                        }
                        if (endTimeText == defaultPlaceholder) {
                            endTime = ""
                        } else {
                            endTime = endTimeText
                        }
                        createdDate = System.currentTimeMillis()
                        sourcePlanet = teamPlanetCode
                        val jo = JsonObject()
                        jo.addProperty("type", "local")
                        jo.addProperty("planetCode", teamPlanetCode)
                        sync = Gson().toJson(jo)
                        if (recurringText != null) {
                            recurring = recurringText
                        }
                        val ob = JsonObject()
                        ob.addProperty("teams", currentTeamId)
                        this.link = Gson().toJson(ob)
                        this.teamId = currentTeamId
                    }
                    val success = meetupRepository.createMeetup(meetup)
                    if (success) {
                        Utilities.toast(activity, getString(R.string.meetup_added))
                        addMeetupDialog?.dismiss()
                        refreshCalendarView()
                        refreshMeetupDialog()
                    } else {
                        Utilities.toast(activity, getString(R.string.meetup_not_added))
                    }
                }
            }
        }

        addMeetupBinding.btnCancel.setOnClickListener {
            addMeetupDialog?.dismiss()
        }

        addMeetupDialog?.setOnDismissListener {
            if (selectedDates.contains(clickedCalendar)) {
                selectedDates.remove(clickedCalendar)
                refreshCalendarView()
            }
        }
        addMeetupDialog?.show()
        addMeetupDialog?.window?.setBackgroundDrawableResource(R.color.card_bg)
    }

    private fun setDatePickerListener(view: TextView, date: Calendar?, endDate: Calendar?) {
        val initCal = date ?: Calendar.getInstance()
        if (date != null && endDate != null) {
            view.text = date.timeInMillis.let { it1 -> TimeUtils.formatDate(it1, "yyyy-MM-dd") }
        }
        view.setOnClickListener {
            DatePickerDialog(requireActivity(), { _, year, monthOfYear, dayOfMonth ->
                date?.set(Calendar.YEAR, year)
                date?.set(Calendar.MONTH, monthOfYear)
                date?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                view.text = date?.timeInMillis?.let { it1 -> TimeUtils.formatDate(it1, "yyyy-MM-dd") }
            }, initCal.get(Calendar.YEAR),
                initCal.get(Calendar.MONTH),
                initCal.get(Calendar.DAY_OF_MONTH)).show()
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
        calendar = binding.calendarView
        calendarEventsMap = mutableMapOf()
        setupCalendarClickListener()
    }

    override fun onResume() {
        super.onResume()
        setupCalendarClickListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupCalendarClickListener(){
        binding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                lifecycleScope.launch {
                    meetupList = meetupRepository.getMeetupsForTeam(teamId)
                    clickedCalendar = calendarDay.calendar
                    val clickedDateInMillis = clickedCalendar.timeInMillis
                    val clickedDate = Instant.ofEpochMilli(clickedDateInMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()

                    val markedDates = meetupList.filter { meetup ->
                        val meetupDate = Instant.ofEpochMilli(meetup.startDate)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        meetupDate == clickedDate
                    }

                    if (markedDates.isNotEmpty()) {
                        showMeetupDialog(markedDates)
                    } else {
                        if(arguments?.getBoolean("fromLogin", false) != false || user?.id?.startsWith("guest") == true){
                            binding.calendarView.selectedDates = eventDates
                        } else{
                            start = clickedCalendar.clone() as Calendar
                            end = clickedCalendar.clone() as Calendar
                            showMeetupAlert()
                        }
                    }
                    if (!selectedDates.contains(clickedCalendar)) {
                        selectedDates.add(clickedCalendar)
                    } else {
                        selectedDates.remove(clickedCalendar)
                    }
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.meetup_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvMeetups)
        val dialogTitle = dialogView.findViewById< TextView>(R.id.tvTitle)
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        dialogTitle.text = dateFormat.format(clickedCalendar.time)
        val extraHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
        ).toInt()
        val cardHeight = getCardViewHeight(requireContext())
        recyclerView.layoutParams.height = cardHeight + extraHeight
        recyclerView.requestLayout()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        meetupAdapter = AdapterMeetup()
        recyclerView.adapter = meetupAdapter
        meetupAdapter?.submitList(meetupList)

        meetupDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        val btnAdd = dialogView.findViewById<Button>(R.id.btnadd)
        if (arguments?.getBoolean("fromLogin", false) != true) {
            btnAdd.visibility = View.VISIBLE
        } else {
            btnAdd.visibility = View.GONE
        }
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            meetupDialog?.dismiss()
        }
        btnAdd.setOnClickListener {
            if(arguments?.getBoolean("fromLogin", false) != true){
                start = clickedCalendar
                end = clickedCalendar
                showMeetupAlert()
            }
        }

        meetupDialog?.setOnDismissListener {
            eventDates.add(clickedCalendar)
            lifecycleScope.launch {
                binding.calendarView.selectedDates = emptyList()
                binding.calendarView.selectedDates = eventDates.toList()
            }
            binding.calendarView.selectedDates = eventDates
        }

        meetupDialog?.show()
    }

    private fun refreshCalendarView() {
        if (teamId.isEmpty()) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val newDates = meetupRepository.getMeetupsForTeam(teamId).mapTo(mutableListOf()) { meetup ->
                val calendarInstance = Calendar.getInstance()
                calendarInstance.timeInMillis = meetup.startDate
                calendarInstance
            }

            if (isAdded && activity != null) {
                eventDates.clear()
                eventDates.addAll(newDates)
                binding.calendarView.selectedDates = ArrayList(newDates)
            }
        }
    }

    private fun refreshMeetupDialog() {
        if (!::clickedCalendar.isInitialized || meetupDialog?.isShowing != true) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedMeetupList = meetupRepository.getMeetupsForTeam(teamId)
            val clickedDateInMillis = clickedCalendar.timeInMillis
            val clickedDate = Instant.ofEpochMilli(clickedDateInMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val filteredMeetups = updatedMeetupList.filter { meetup ->
                val meetupDate = Instant.ofEpochMilli(meetup.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                meetupDate == clickedDate
            }

            meetupAdapter?.submitList(filteredMeetups)
        }
    }


}
