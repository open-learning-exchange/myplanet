package org.ole.planet.myplanet.ui.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentCalendarBinding
import java.util.Calendar

class CalendarFragment : Fragment() {
    private lateinit var calendarBinding: FragmentCalendarBinding
    var listener: OnHomeItemClickListener? = null
    fun addEvents() {
        val events: MutableList<EventDay> = ArrayList()
        val calendar = Calendar.getInstance()
        events.add(EventDay(calendar, R.drawable.bg_label_checked))
        calendarBinding.calendarView.setEvents(events)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        calendarBinding = FragmentCalendarBinding.inflate(inflater, container, false)
        calendarBinding.calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {

            }
        })
        val calendar = Calendar.getInstance()
        calendarBinding.calendarView.setDate(calendar.time)
        return calendarBinding.root
    }
}
