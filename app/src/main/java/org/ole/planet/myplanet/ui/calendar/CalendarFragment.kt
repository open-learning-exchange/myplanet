package org.ole.planet.myplanet.ui.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentCalendarBinding
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.ui.events.EventsAdapter
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CalendarFragment : BaseBindingFragment<FragmentCalendarBinding>(FragmentCalendarBinding::inflate) {
    private val viewModel: CalendarViewModel by viewModels()
    private var meetups: List<Meetup> = emptyList()
    var listener: OnHomeItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.calendarView.setDate(Calendar.getInstance().time)

        collectWhenStarted(viewModel.meetups) { updatedMeetups ->
            meetups = updatedMeetups
            val calendarDays = updatedMeetups.map { meetup ->
                CalendarDay(Calendar.getInstance().apply { timeInMillis = meetup.startDate }).apply {
                    imageResource = R.drawable.ic_calendar
                }
            }
            binding.calendarView.setCalendarDays(calendarDays)
        }

        binding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val clickedDate = Instant.ofEpochMilli(calendarDay.calendar.timeInMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val dayMeetups = meetups.filter { meetup ->
                    Instant.ofEpochMilli(meetup.startDate).atZone(ZoneId.systemDefault()).toLocalDate() == clickedDate
                }
                if (dayMeetups.isNotEmpty()) {
                    showAgendaDialog(dayMeetups)
                }
            }
        })
    }

    private fun showAgendaDialog(dayMeetups: List<Meetup>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.calendar_agenda_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvMeetups)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = EventsAdapter(onMeetupClick = null) { meetup ->
            viewModel.teamNames.value[meetup.teamId]
        }.apply { submitList(dayMeetups) }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
