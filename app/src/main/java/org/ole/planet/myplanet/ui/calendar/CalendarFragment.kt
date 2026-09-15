package org.ole.planet.myplanet.ui.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentCalendarBinding
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.ui.events.EventsAdapter
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()
    private var meetups: List<Meetup> = emptyList()
    var listener: OnHomeItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
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

        viewModel.loadMeetups()
    }

    private fun showAgendaDialog(dayMeetups: List<Meetup>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.meetup_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvMeetups)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = EventsAdapter(onMeetupClick = null) { meetup ->
            viewModel.teamNames.value[meetup.teamId]
        }.apply { submitList(dayMeetups) }
        dialogView.findViewById<View>(R.id.btnadd).visibility = View.GONE

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
